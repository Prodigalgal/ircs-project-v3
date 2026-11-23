--liquibase formatted sql

--changeset codex:1795470000005-catalog-ownership-functions splitStatements:false
CREATE FUNCTION resolve_catalog_episode_season() RETURNS trigger
LANGUAGE plpgsql AS $body$
BEGIN
    IF NEW.season_number IS NOT NULL AND NEW.season_id IS NULL THEN
        SELECT s.id INTO STRICT NEW.season_id FROM catalog_seasons s
         WHERE s.work_id = NEW.work_id AND s.season_number = NEW.season_number;
    END IF;
    RETURN NEW;
END;
$body$;

CREATE TRIGGER catalog_episode_resolve_season
BEFORE INSERT OR UPDATE OF work_id, season_number, season_id ON catalog_episodes
FOR EACH ROW EXECUTE FUNCTION resolve_catalog_episode_season();

CREATE FUNCTION resolve_catalog_credit_scope() RETURNS trigger
LANGUAGE plpgsql AS $body$
BEGIN
    IF NEW.season_number IS NOT NULL THEN
        SELECT s.id INTO STRICT NEW.season_id FROM catalog_seasons s
         WHERE s.work_id = NEW.work_id AND s.season_number = NEW.season_number;
    END IF;
    IF NEW.episode_number IS NOT NULL THEN
        SELECT ep.id INTO STRICT NEW.episode_id FROM catalog_episodes ep
         WHERE ep.work_id = NEW.work_id AND ep.season_id = NEW.season_id AND ep.episode_number = NEW.episode_number;
    END IF;
    RETURN NEW;
END;
$body$;

CREATE TRIGGER catalog_credit_resolve_scope
BEFORE INSERT OR UPDATE OF work_id, season_number, episode_number ON catalog_person_credits
FOR EACH ROW EXECUTE FUNCTION resolve_catalog_credit_scope();

CREATE FUNCTION initialize_catalog_publication_scope() RETURNS trigger
LANGUAGE plpgsql AS $body$
DECLARE
    scope_number integer;
BEGIN
    IF NEW.work_id IS NULL THEN
        NEW.work_id := uuidv7();
        INSERT INTO catalog_works(id, media_type, title, original_title, title_key, original_title_key,
            release_year, released_on, category_id, identity_status)
        VALUES (NEW.work_id,
            CASE WHEN NEW.category_code IN ('series','anime','short-drama','variety') OR NEW.season IS NOT NULL
                THEN 'tv' ELSE 'movie' END,
            NEW.title, NEW.alias_title, lower(btrim(NEW.title)), lower(btrim(NEW.alias_title)),
            CASE WHEN NEW.year ~ '^(18|19|20|21)[0-9]{2}$' THEN NEW.year::smallint END,
            NEW.published_at, NEW.category_id, 'PROVISIONAL');
    END IF;
    IF NEW.season_id IS NULL AND NEW.season > 0 THEN
        INSERT INTO catalog_seasons(work_id, season_number) VALUES (NEW.work_id, NEW.season)
        ON CONFLICT (work_id, season_number) DO NOTHING;
        SELECT s.id INTO STRICT NEW.season_id FROM catalog_seasons s
         WHERE s.work_id = NEW.work_id AND s.season_number = NEW.season;
    END IF;
    IF NEW.season_id IS NOT NULL THEN
        SELECT s.season_number INTO STRICT scope_number FROM catalog_seasons s
         WHERE s.work_id = NEW.work_id AND s.id = NEW.season_id;
        NEW.season := scope_number;
    END IF;
    RETURN NEW;
END;
$body$;

CREATE TRIGGER catalog_entry_initialize_scope
BEFORE INSERT OR UPDATE OF work_id, season_id ON catalog_entries
FOR EACH ROW EXECUTE FUNCTION initialize_catalog_publication_scope();

CREATE FUNCTION record_catalog_work_titles() RETURNS trigger
LANGUAGE plpgsql AS $body$
BEGIN
    INSERT INTO catalog_work_titles(work_id, title_kind, title, title_key, provider)
    SELECT NEW.id, fact.kind, fact.title, fact.title_key,
        CASE WHEN NEW.identity_status = 'VERIFIED' THEN 'TMDB' ELSE 'SOURCE' END
    FROM (VALUES ('PRIMARY', NEW.title, NEW.title_key),
                 ('ORIGINAL', NEW.original_title, NEW.original_title_key)) fact(kind, title, title_key)
    WHERE nullif(btrim(fact.title), '') IS NOT NULL AND nullif(btrim(fact.title_key), '') IS NOT NULL
    ON CONFLICT DO NOTHING;
    RETURN NEW;
END;
$body$;

CREATE TRIGGER catalog_work_record_titles
AFTER INSERT OR UPDATE OF title, title_key, original_title, original_title_key, identity_status ON catalog_works
FOR EACH ROW EXECUTE FUNCTION record_catalog_work_titles();

--changeset codex:1795470000006-catalog-source-and-playback-bindings
CREATE TABLE catalog_source_match_evidence (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    source_record_id uuid NOT NULL REFERENCES source_records(id) ON DELETE CASCADE,
    source_record_version_id uuid,
    catalog_entry_id uuid NOT NULL REFERENCES catalog_entries(id) ON DELETE CASCADE,
    evidence_kind varchar(40) NOT NULL CHECK (btrim(evidence_kind) <> ''),
    algorithm_version varchar(16) NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE NULLS NOT DISTINCT (source_record_id, catalog_entry_id, source_record_version_id, evidence_kind, algorithm_version),
    UNIQUE (source_record_id, catalog_entry_id, id),
    FOREIGN KEY (source_record_id, source_record_version_id)
        REFERENCES source_record_versions(source_record_id, id) ON DELETE CASCADE
);

ALTER TABLE catalog_source_bindings ADD COLUMN work_id uuid NOT NULL,
    ADD COLUMN evidence_id uuid NOT NULL,
    ADD COLUMN bound_at timestamptz NOT NULL DEFAULT now(),
    ADD CONSTRAINT catalog_source_binding_current UNIQUE (source_record_id),
    ADD CONSTRAINT catalog_source_binding_work_scope UNIQUE (source_record_id, work_id),
    ADD CONSTRAINT catalog_source_binding_entry_fkey FOREIGN KEY (work_id, catalog_entry_id)
        REFERENCES catalog_entries(work_id, id) ON DELETE CASCADE,
    ADD CONSTRAINT catalog_source_binding_evidence_fkey FOREIGN KEY (source_record_id, catalog_entry_id, evidence_id)
        REFERENCES catalog_source_match_evidence(source_record_id, catalog_entry_id, id);

CREATE TABLE catalog_playback_bindings (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    source_record_id uuid NOT NULL,
    source_playback_item_id uuid NOT NULL UNIQUE,
    work_id uuid NOT NULL,
    season_id uuid,
    episode_id uuid,
    season_key uuid GENERATED ALWAYS AS (coalesce(season_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED,
    target_kind varchar(8) NOT NULL,
    verified_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (source_record_id, source_playback_item_id)
        REFERENCES source_playback_items(source_record_id, id) ON DELETE CASCADE,
    FOREIGN KEY (source_record_id, work_id)
        REFERENCES catalog_source_bindings(source_record_id, work_id) ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (work_id, season_id) REFERENCES catalog_seasons(work_id, id) ON DELETE CASCADE,
    FOREIGN KEY (work_id, episode_id) REFERENCES catalog_episodes(work_id, id) ON DELETE CASCADE,
    FOREIGN KEY (work_id, season_id, episode_id)
        REFERENCES catalog_episodes(work_id, season_id, id) ON DELETE CASCADE,
    CHECK ((target_kind = 'WORK' AND season_id IS NULL AND episode_id IS NULL)
        OR (target_kind = 'SEASON' AND season_id IS NOT NULL AND episode_id IS NULL)
        OR (target_kind = 'EPISODE' AND episode_id IS NOT NULL))
);
ALTER TABLE catalog_episodes
    ADD COLUMN season_key uuid GENERATED ALWAYS AS (coalesce(season_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED,
    ADD CONSTRAINT catalog_episode_exact_scope UNIQUE (work_id, season_key, id);
ALTER TABLE catalog_playback_bindings ADD CONSTRAINT catalog_playback_exact_episode_scope
    FOREIGN KEY (work_id, season_key, episode_id) REFERENCES catalog_episodes(work_id, season_key, id) ON DELETE CASCADE;
CREATE INDEX catalog_playback_binding_target ON catalog_playback_bindings(work_id, season_id, episode_id);

CREATE TABLE playback_availability_checks (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    playback_binding_id uuid NOT NULL REFERENCES catalog_playback_bindings(id) ON DELETE CASCADE,
    checked_at timestamptz NOT NULL DEFAULT now(),
    availability varchar(16) NOT NULL CHECK (availability IN ('AVAILABLE', 'UNAVAILABLE', 'UNKNOWN')),
    response_code smallint CHECK (response_code BETWEEN 100 AND 599),
    elapsed_ms integer CHECK (elapsed_ms >= 0),
    error_class varchar(80)
);
CREATE INDEX playback_availability_latest ON playback_availability_checks(playback_binding_id, checked_at DESC);

--changeset codex:1795470000007-catalog-playback-reconciliation splitStatements:false
CREATE FUNCTION capture_catalog_source_binding() RETURNS trigger
LANGUAGE plpgsql AS $body$
DECLARE
    observed_version uuid;
BEGIN
    SELECT r.current_version_id INTO STRICT observed_version FROM source_records r WHERE r.id = NEW.source_record_id;
    SELECT e.work_id INTO STRICT NEW.work_id FROM catalog_entries e WHERE e.id = NEW.catalog_entry_id;
    IF TG_OP = 'UPDATE' AND OLD.catalog_entry_id <> NEW.catalog_entry_id THEN
        DELETE FROM catalog_playback_bindings WHERE source_record_id = NEW.source_record_id;
        NEW.evidence_id := NULL;
    END IF;
    IF NEW.evidence_id IS NULL THEN
        INSERT INTO catalog_source_match_evidence(source_record_id, source_record_version_id,
            catalog_entry_id, evidence_kind, algorithm_version)
        VALUES (NEW.source_record_id, observed_version, NEW.catalog_entry_id, 'SOURCE_CLUSTER', '5.0')
        ON CONFLICT DO NOTHING;
        SELECT ev.id INTO STRICT NEW.evidence_id FROM catalog_source_match_evidence ev
         WHERE ev.source_record_id = NEW.source_record_id AND ev.catalog_entry_id = NEW.catalog_entry_id
           AND ev.source_record_version_id IS NOT DISTINCT FROM observed_version
           AND ev.evidence_kind = 'SOURCE_CLUSTER' AND ev.algorithm_version = '5.0';
    END IF;
    NEW.bound_at := now();
    RETURN NEW;
END;
$body$;

CREATE TRIGGER catalog_source_capture_binding
BEFORE INSERT OR UPDATE OF catalog_entry_id, evidence_id ON catalog_source_bindings
FOR EACH ROW EXECUTE FUNCTION capture_catalog_source_binding();

CREATE FUNCTION affirm_catalog_source_match(target_source uuid, target_entry uuid, match_kind text) RETURNS integer
LANGUAGE plpgsql AS $body$
DECLARE
    observed_version uuid;
    evidence uuid;
    changed integer;
BEGIN
    SELECT current_version_id INTO observed_version FROM source_records WHERE id = target_source FOR UPDATE;
    IF NOT FOUND OR NOT EXISTS (SELECT FROM catalog_source_bindings
        WHERE source_record_id = target_source AND catalog_entry_id = target_entry) THEN
        RETURN 0;
    END IF;
    INSERT INTO catalog_source_match_evidence(source_record_id, source_record_version_id,
        catalog_entry_id, evidence_kind, algorithm_version)
    VALUES (target_source, observed_version, target_entry, match_kind, '5.0')
    ON CONFLICT DO NOTHING;
    SELECT ev.id INTO STRICT evidence FROM catalog_source_match_evidence ev
     WHERE ev.source_record_id = target_source AND ev.catalog_entry_id = target_entry
       AND ev.source_record_version_id IS NOT DISTINCT FROM observed_version
       AND ev.evidence_kind = match_kind AND ev.algorithm_version = '5.0';
    UPDATE catalog_source_bindings SET evidence_id = evidence
     WHERE source_record_id = target_source AND catalog_entry_id = target_entry;
    GET DIAGNOSTICS changed = ROW_COUNT;
    RETURN changed;
END;
$body$;

CREATE FUNCTION reconcile_catalog_playback(target_source uuid) RETURNS void
LANGUAGE plpgsql AS $body$
DECLARE
    binding record;
BEGIN
    SELECT b.work_id, entry.season_id, season.season_number, entry.season AS display_season,
        source.season AS source_season, work.media_type, work.identity_status,
        source.current_version_id, evidence.source_record_version_id, evidence.evidence_kind
    INTO binding
    FROM catalog_source_bindings b
    JOIN catalog_entries entry ON entry.id = b.catalog_entry_id
    JOIN catalog_works work ON work.id = b.work_id
    JOIN source_records source ON source.id = b.source_record_id
    JOIN catalog_source_match_evidence evidence ON evidence.id = b.evidence_id
    LEFT JOIN catalog_seasons season ON season.id = entry.season_id
    WHERE b.source_record_id = target_source;
    IF NOT FOUND OR (binding.identity_status = 'VERIFIED' AND (
            binding.evidence_kind = 'SOURCE_CLUSTER'
            OR binding.source_record_version_id IS DISTINCT FROM binding.current_version_id)) THEN
        DELETE FROM catalog_playback_bindings WHERE source_record_id = target_source;
        RETURN;
    END IF;

    -- Source-first serials get stable IDs from their active publication scope. Explicit conflicting seasons are skipped.
    IF binding.identity_status = 'PROVISIONAL' AND binding.media_type = 'tv' THEN
        INSERT INTO catalog_seasons(work_id,season_number)
        SELECT DISTINCT binding.work_id, resolved.season_number
        FROM source_playback_items item
        CROSS JOIN LATERAL (
            SELECT substring(upper(btrim(item.name)) FROM '^S([0-9]{1,4})E')::integer AS label_season
        ) label
        CROSS JOIN LATERAL (
            SELECT coalesce(label.label_season,binding.source_season,binding.season_number) AS season_number
        ) resolved
        WHERE item.source_record_id=target_source AND resolved.season_number>=0
          AND (binding.source_season IS NULL OR resolved.season_number=binding.source_season)
          AND (binding.season_number IS NULL OR resolved.season_number=binding.season_number)
        ON CONFLICT (work_id,season_number) DO NOTHING;
        INSERT INTO catalog_episodes(work_id, season_id, season_number, episode_number, title)
        SELECT DISTINCT ON (scope.season_number, episode_number) binding.work_id, scope.id, scope.season_number,
            label.episode_number, left(item.name, 500)
        FROM source_playback_items item
        CROSS JOIN LATERAL (SELECT catalog_episode_number(item.name) AS episode_number,
            substring(upper(btrim(item.name)) FROM '^S([0-9]{1,4})E')::integer AS label_season) label
        LEFT JOIN catalog_seasons scope ON scope.work_id=binding.work_id
            AND scope.season_number=coalesce(label.label_season,binding.source_season,binding.season_number)
        WHERE item.source_record_id=target_source AND label.episode_number>0
          AND (label.label_season IS NULL OR scope.id IS NOT NULL)
          AND (binding.source_season IS NULL OR scope.season_number=binding.source_season)
          AND (binding.season_number IS NULL OR scope.season_number=binding.season_number)
        ORDER BY scope.season_number,label.episode_number,item.id
        ON CONFLICT (work_id, season_number, episode_number) DO NOTHING;
    END IF;

    WITH candidates AS MATERIALIZED (
        SELECT item.id AS source_playback_item_id, season.id AS season_id, ep.id AS episode_id,
            CASE WHEN ep.id IS NOT NULL THEN 'EPISODE' WHEN season.id IS NOT NULL THEN 'SEASON' ELSE 'WORK' END AS target_kind
        FROM source_playback_items item
        CROSS JOIN LATERAL (SELECT catalog_episode_number(item.name) AS episode_number,
            substring(upper(btrim(item.name)) FROM '^S([0-9]{1,4})E')::integer AS label_season) label
        LEFT JOIN catalog_seasons season ON season.work_id = binding.work_id
            AND season.season_number = coalesce(label.label_season, binding.source_season, binding.season_number)
        LEFT JOIN catalog_episodes ep ON ep.work_id = binding.work_id
            AND ep.season_id IS NOT DISTINCT FROM season.id AND ep.episode_number = label.episode_number
        WHERE item.source_record_id = target_source
          AND (label.label_season IS NULL OR binding.source_season IS NULL OR label.label_season = binding.source_season)
          AND (binding.source_season IS NULL OR binding.season_number IS NULL OR binding.source_season = binding.season_number)
          AND (label.label_season IS NULL OR season.id IS NOT NULL)
          AND (binding.identity_status <> 'VERIFIED' OR binding.media_type = 'movie'
               OR (binding.media_type = 'tv' AND ep.tmdb_episode_id IS NOT NULL AND season.verified_at IS NOT NULL))
    ), removed AS (
        DELETE FROM catalog_playback_bindings old
        WHERE old.source_record_id = target_source AND NOT EXISTS (
            SELECT FROM candidates candidate WHERE candidate.source_playback_item_id = old.source_playback_item_id)
    )
    INSERT INTO catalog_playback_bindings(source_record_id, source_playback_item_id, work_id, season_id, episode_id, target_kind)
    SELECT target_source, source_playback_item_id, binding.work_id, season_id, episode_id, target_kind
    FROM candidates
    ON CONFLICT (source_playback_item_id) DO UPDATE SET work_id = excluded.work_id, season_id = excluded.season_id,
        episode_id = excluded.episode_id, target_kind = excluded.target_kind, verified_at = now()
    WHERE (catalog_playback_bindings.work_id, catalog_playback_bindings.season_id, catalog_playback_bindings.episode_id,
          catalog_playback_bindings.target_kind) IS DISTINCT FROM
          (excluded.work_id, excluded.season_id, excluded.episode_id, excluded.target_kind);
END;
$body$;

CREATE FUNCTION refresh_catalog_playback_binding() RETURNS trigger
LANGUAGE plpgsql AS $body$
BEGIN
    PERFORM reconcile_catalog_playback(NEW.source_record_id);
    RETURN NEW;
END;
$body$;

CREATE TRIGGER catalog_source_refresh_playback
AFTER INSERT OR UPDATE OF catalog_entry_id, evidence_id ON catalog_source_bindings
FOR EACH ROW EXECUTE FUNCTION refresh_catalog_playback_binding();

DROP FUNCTION catalog_has_target_resources(uuid, integer, integer);
DROP FUNCTION catalog_resource_inventory(uuid, integer);
DROP FUNCTION catalog_previous_has_target_resources(uuid, integer, integer);
DROP FUNCTION catalog_previous_resource_inventory(uuid, integer);
DROP FUNCTION catalog_legacy_has_target_resources(uuid, integer, integer);
DROP FUNCTION catalog_legacy_resource_inventory(uuid, integer);
DROP FUNCTION catalog_list_playable_episodes(uuid);
DROP FUNCTION catalog_list_scope_matched_episodes(uuid);

CREATE FUNCTION catalog_list_playable_episodes(target_entry uuid)
RETURNS TABLE(source_id uuid, source_name text, playlist_name text, episode_id uuid, episode_name text,
    episode_url text, episode_domain_id uuid, season_number integer, episode_number integer, catalog_episode_id uuid)
LANGUAGE sql STABLE PARALLEL SAFE AS $body$
    SELECT item.source_record_id, provider.name::text, playlist.name::text, item.id, item.name::text,
        item.url::text, item.source_origin_id, season.season_number, episode.episode_number, episode.id
    FROM catalog_entries entry
    JOIN catalog_playback_bindings playback ON playback.work_id = entry.work_id
        AND (entry.season_id IS NULL OR playback.season_id=entry.season_id)
    JOIN catalog_source_bindings source_binding ON source_binding.source_record_id = playback.source_record_id
    JOIN catalog_source_match_evidence evidence ON evidence.id = source_binding.evidence_id
    JOIN source_records source ON source.id = playback.source_record_id
    JOIN catalog_works work ON work.id = entry.work_id
    JOIN source_playback_items item ON item.id = playback.source_playback_item_id
    JOIN source_playlists playlist ON playlist.id = item.playlist_id
    LEFT JOIN collection_sources provider ON provider.id = source.collection_source_id
    LEFT JOIN catalog_seasons season ON season.id = playback.season_id
    LEFT JOIN catalog_episodes episode ON episode.id = playback.episode_id
    WHERE entry.id = target_entry
        AND (work.identity_status <> 'VERIFIED' OR (evidence.evidence_kind <> 'SOURCE_CLUSTER'
             AND evidence.source_record_version_id IS NOT DISTINCT FROM source.current_version_id))
        AND (btrim(item.url) ~* '^https?://[^[:space:]]+$'
             OR (btrim(item.url) ~ '^/[^[:space:]]+$' AND item.source_origin_id IS NOT NULL))
        AND NOT coalesce(provider.adult_restricted, false)
        AND NOT entry.adult_restricted
        AND lower(coalesce(entry.adult_assessment ->> 'adultRestricted', 'false')) = 'false'
        AND upper(coalesce(entry.adult_assessment ->> 'level', 'SAFE')) <> 'ADULT'
$body$;

CREATE FUNCTION catalog_has_target_resources(target_entry uuid, required_season integer, required_episode integer)
RETURNS boolean LANGUAGE sql STABLE PARALLEL SAFE AS $body$
    SELECT EXISTS (
        SELECT FROM catalog_list_playable_episodes(target_entry) playback
        WHERE (required_season = 0 OR playback.season_number = required_season)
          AND (required_episode = 0 OR playback.episode_number = required_episode)
    ) OR (required_season = 0 AND required_episode = 0 AND EXISTS (
        SELECT FROM catalog_entries entry JOIN catalog_magnet_links magnet ON magnet.catalog_entry_id = entry.id
        WHERE entry.id = target_entry AND magnet.status = 'APPROVED' AND entry.content_visibility = 'PUBLIC'
          AND NOT entry.adult_restricted AND entry.season_id IS NULL
          AND lower(coalesce(entry.adult_assessment ->> 'adultRestricted', 'false')) = 'false'
          AND upper(coalesce(entry.adult_assessment ->> 'level', 'SAFE')) <> 'ADULT'
    ))
$body$;

CREATE FUNCTION catalog_resource_inventory(target_entry uuid, required_season integer)
RETURNS TABLE(resource_available boolean, available_episode integer)
LANGUAGE sql STABLE PARALLEL SAFE AS $body$
    SELECT count(*) > 0, max(playback.episode_number)
    FROM catalog_list_playable_episodes(target_entry) playback
    WHERE required_season = 0 OR playback.season_number = required_season
$body$;

--changeset codex:1795470000008-catalog-summary-and-outbox splitStatements:false
ALTER TABLE catalog_metadata_enrichment_jobs DROP COLUMN index_pending, DROP COLUMN index_generation;

CREATE TABLE catalog_entry_summaries (
    catalog_entry_id uuid PRIMARY KEY REFERENCES catalog_entries(id) ON DELETE CASCADE,
    work_id uuid NOT NULL,
    title varchar(255) NOT NULL,
    media_image_source_id uuid,
    category_code varchar(100),
    content_visibility varchar(32) NOT NULL,
    display_facts jsonb NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (work_id, catalog_entry_id) REFERENCES catalog_entries(work_id, id) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE SEQUENCE catalog_projection_revision;
CREATE TABLE catalog_projection_changes (
    catalog_entry_id uuid PRIMARY KEY,
    revision bigint NOT NULL DEFAULT nextval('catalog_projection_revision'),
    operation varchar(8) NOT NULL CHECK (operation IN ('INDEX', 'DELETE')),
    changed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX catalog_projection_due ON catalog_projection_changes(changed_at, catalog_entry_id);

CREATE FUNCTION queue_catalog_projection(entry_id uuid, change_kind text) RETURNS void
LANGUAGE sql AS $body$
    INSERT INTO catalog_projection_changes(catalog_entry_id, operation) VALUES (entry_id, change_kind)
    ON CONFLICT (catalog_entry_id) DO UPDATE SET revision = excluded.revision,
        operation = excluded.operation, changed_at = excluded.changed_at
$body$;

CREATE FUNCTION project_catalog_entry() RETURNS trigger
LANGUAGE plpgsql AS $body$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM queue_catalog_projection(OLD.id, 'DELETE');
        RETURN OLD;
    END IF;
    INSERT INTO catalog_entry_summaries(catalog_entry_id, work_id, title, media_image_source_id,
        category_code, content_visibility, display_facts)
    VALUES (NEW.id, NEW.work_id, NEW.title, NEW.media_image_source_id, NEW.category_code, NEW.content_visibility,
        jsonb_build_object('description', NEW.description, 'year', NEW.year, 'score', NEW.score,
            'publishedOn', NEW.published_at, 'aliasTitle', NEW.alias_title, 'subtitle', NEW.subtitle,
            'season', NEW.season, 'actors', NEW.actor_names, 'directors', NEW.director_names,
            'genres', NEW.genre_codes, 'regions', NEW.area_codes, 'languages', NEW.language_codes))
    ON CONFLICT (catalog_entry_id) DO UPDATE SET work_id = excluded.work_id, title = excluded.title,
        media_image_source_id = excluded.media_image_source_id, category_code = excluded.category_code,
        content_visibility = excluded.content_visibility, display_facts = excluded.display_facts, updated_at = now();
    IF NEW.adult_checked_at IS NOT NULL AND (TG_OP = 'INSERT' OR OLD.adult_checked_at IS DISTINCT FROM NEW.adult_checked_at) THEN
        INSERT INTO catalog_content_assessments(catalog_entry_id, policy_version, assessment, restricted, assessed_at)
        VALUES (NEW.id, '5.0', NEW.adult_assessment, NEW.adult_restricted, NEW.adult_checked_at AT TIME ZONE 'UTC')
        ON CONFLICT DO NOTHING;
    END IF;
    PERFORM queue_catalog_projection(NEW.id, CASE WHEN NEW.content_visibility = 'HIDDEN' OR NEW.adult_restricted
        THEN 'DELETE' ELSE 'INDEX' END);
    RETURN NEW;
END;
$body$;

CREATE TRIGGER catalog_entry_write_projection
AFTER INSERT OR DELETE OR UPDATE OF work_id, season_id, title, alias_title, subtitle, published_at, season,
    media_image_source_id, category_code, content_visibility, score, year, description, actor_names, director_names,
    genre_codes, area_codes, language_codes, adult_checked_at, adult_restricted ON catalog_entries
FOR EACH ROW EXECUTE FUNCTION project_catalog_entry();
