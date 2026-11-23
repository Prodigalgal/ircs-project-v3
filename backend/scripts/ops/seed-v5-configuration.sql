\set ON_ERROR_STOP on

BEGIN;
SELECT set_config('ircs.seed.source_database', :'source_database', true) IS NOT NULL AS source_selected \gset
CREATE EXTENSION IF NOT EXISTS dblink;

DO $seed$
DECLARE
    source_database text := current_setting('ircs.seed.source_database');
    source_connection text;
    relation record;
    source_query text;
BEGIN
    IF source_database !~ '^[a-z][a-z0-9_]{0,62}$' OR source_database = current_database() THEN
        RAISE EXCEPTION 'A separate legacy IRCS database is required';
    END IF;
    IF NOT EXISTS (SELECT FROM databasechangelog WHERE tag ~ '^5\.0\.[0-9]+$') THEN
        RAISE EXCEPTION 'The destination must be initialized by an IRCS 5.0 migrator';
    END IF;
    IF EXISTS (SELECT FROM catalog_works) OR EXISTS (SELECT FROM source_records)
        OR EXISTS (SELECT FROM member_accounts) OR EXISTS (SELECT FROM collection_runs) THEN
        RAISE EXCEPTION 'Configuration initialization requires a catalog without business records';
    END IF;
    source_connection := format('dbname=%s user=%s',source_database,current_user);

    -- Dependencies are loaded in order. Network origins and execution state are discovered again by 5.0.
    DELETE FROM collection_plans;
    FOR relation IN
        SELECT * FROM (VALUES
            ('data_sources', 'collection_sources'),
            ('standard_category', 'catalog_categories'),
            ('standard_genre', 'catalog_genres'),
            ('standard_areas', 'catalog_regions'),
            ('standard_languages', 'catalog_languages'),
            ('sys_credentials', 'integration_credentials'),
            ('system_configs', 'application_settings'),
            ('admin_api_tokens', 'admin_api_access_tokens'),
            ('magnet_providers', 'magnet_discovery_providers'),
            ('video_resolver_sources', 'playback_resolver_profiles'),
            ('collection_tasks', 'collection_plans')
        ) map(legacy_table, target_table)
    LOOP
        source_query := CASE relation.legacy_table
            WHEN 'collection_tasks' THEN
                'select to_jsonb(s) || jsonb_build_object(''collection_source_id'',data_source_id,''enabled'',false)
                 from public.collection_tasks s'
            WHEN 'magnet_providers' THEN
                'select to_jsonb(s) - array[''last_health_check_at'',''last_health_status'',''last_error_message'']
                 from public.magnet_providers s'
            ELSE format('select to_jsonb(s) from public.%I s',relation.legacy_table)
        END;
        EXECUTE format('delete from public.%I',relation.target_table);
        EXECUTE format(
            'insert into public.%1$I select (jsonb_populate_record(null::public.%1$I,data)).*
             from dblink($1,$2) as transfer(data jsonb)', relation.target_table)
            USING source_connection,source_query;
    END LOOP;

    UPDATE application_settings SET config_value='false'
    WHERE config_key IN ('app.task.catalog-discovery.enabled','app.scraper.trend-sync.enabled',
        'app.magnet.auto-search.enabled','app.catalog.authority-first.enabled');
END;
$seed$;

SELECT (SELECT count(*) FROM collection_sources) AS configured_sources,
       (SELECT count(*) FROM collection_plans WHERE NOT enabled) AS paused_plans,
       (SELECT count(*) FROM integration_credentials) AS integration_credentials,
       (SELECT count(*) FROM catalog_works) AS catalog_works,
       (SELECT count(*) FROM collection_runs) AS collection_runs;
COMMIT;
