--liquibase formatted sql

--changeset codex:1795470000000-require-empty-business-database splitStatements:false
DO $guard$
BEGIN
    IF EXISTS (SELECT FROM unified_videos) OR EXISTS (SELECT FROM raw_videos)
        OR EXISTS (SELECT FROM members) OR EXISTS (SELECT FROM portal_media_requests) THEN
        RAISE EXCEPTION 'IRCS 5.0 must initialize a fresh catalog database. The current database contains business records.';
    END IF;
END
$guard$;

--changeset codex:1795470000001-domain-table-names
--comment: Establish the IRCS 5.0 business vocabulary across stored relations.

ALTER TABLE admin_api_tokens RENAME TO admin_api_access_tokens;
ALTER TABLE audit_archive_entries RENAME TO archived_audit_events;
ALTER TABLE authority_catalog_episodes RENAME TO catalog_episodes;
ALTER TABLE authority_catalog_imports RENAME TO catalog_authority_import_jobs;
ALTER TABLE authority_catalog_projections RENAME TO catalog_authority_publications;
ALTER TABLE authority_catalog_seasons RENAME TO catalog_seasons;
ALTER TABLE authority_catalog_works RENAME TO catalog_works;
ALTER TABLE authority_resource_bindings RENAME TO catalog_source_match_evidence;
ALTER TABLE authority_tmdb_changes_cursor RENAME TO provider_catalog_sync_cursors;
ALTER VIEW authority_tmdb_inventory RENAME TO active_provider_catalog_items;
ALTER TABLE authority_tmdb_inventory_entries RENAME TO provider_catalog_snapshot_items;
ALTER TABLE authority_tmdb_inventory_state RENAME TO provider_catalog_snapshot_states;
ALTER TABLE catalog_credits RENAME TO catalog_person_credits;
ALTER TABLE catalog_discovery_requests RENAME TO catalog_resource_discovery_jobs;
ALTER TABLE catalog_metadata_requests RENAME TO catalog_metadata_enrichment_jobs;
ALTER TABLE collection_tasks RENAME TO collection_plans;
ALTER TABLE cover_images RENAME TO media_image_sources;
ALTER TABLE data_source_domains RENAME TO collection_source_origins;
ALTER TABLE data_sources RENAME TO collection_sources;
ALTER TABLE episodes RENAME TO source_playback_items;
ALTER TABLE external_catalog_events RENAME TO catalog_release_events;
ALTER TABLE external_catalog_identities RENAME TO catalog_external_ids;
ALTER VIEW external_catalog_rankings RENAME TO catalog_entry_rankings;
ALTER VIEW external_catalog_recent_events RENAME TO catalog_entry_recent_release_events;
ALTER TABLE external_trend_boards RENAME TO provider_ranking_boards;
ALTER TABLE external_trend_observations RENAME TO provider_ranking_observations;
ALTER TABLE failed_messages RENAME TO message_processing_failures;
ALTER TABLE magnet_links RENAME TO catalog_magnet_links;
ALTER TABLE magnet_provider_runs RENAME TO magnet_discovery_provider_runs;
ALTER TABLE magnet_providers RENAME TO magnet_discovery_providers;
ALTER TABLE magnet_search_jobs RENAME TO magnet_discovery_jobs;
ALTER TABLE maintenance_operations RENAME TO operations_maintenance_runs;
ALTER TABLE member_favorites RENAME TO member_catalog_favorites;
ALTER TABLE member_oauth_accounts RENAME TO member_login_identities;
ALTER TABLE member_watch_histories RENAME TO member_playback_progress;
ALTER TABLE members RENAME TO member_accounts;
ALTER TABLE notification_mail_send_history RENAME TO mail_delivery_attempts;
ALTER TABLE ops_alert_events RENAME TO operations_alert_events;
ALTER TABLE ops_dashboard_read_model_snapshots RENAME TO operations_dashboard_display_snapshots;
ALTER TABLE ops_dashboard_stats_snapshots RENAME TO operations_pipeline_metric_snapshots;
ALTER TABLE ops_healing_actions RENAME TO operations_remediation_actions;
ALTER TABLE ops_incidents RENAME TO operations_incidents;
ALTER TABLE ops_request_audit_summary_snapshots RENAME TO operations_http_request_summary_snapshots;
ALTER TABLE people RENAME TO catalog_people;
ALTER TABLE person_external_ids RENAME TO catalog_person_external_ids;
ALTER TABLE playlists RENAME TO source_playlists;
ALTER TABLE portal_media_request_batch_items RENAME TO media_request_processing_batch_items;
ALTER TABLE portal_media_request_batches RENAME TO media_request_processing_batches;
ALTER TABLE portal_media_requests RENAME TO media_request_demands;
ALTER TABLE raw_video_enrichment_provider_runs RENAME TO source_metadata_provider_runs;
ALTER TABLE raw_video_pipeline_runs RENAME TO source_record_processing_runs;
ALTER TABLE raw_video_unified_video RENAME TO catalog_source_bindings;
ALTER TABLE raw_videos RENAME TO source_records;
ALTER TABLE request_audit_logs RENAME TO http_request_audit_events;
ALTER TABLE source_domains RENAME TO source_origins;
ALTER TABLE standard_areas RENAME TO catalog_regions;
ALTER TABLE standard_category RENAME TO catalog_categories;
ALTER TABLE standard_genre RENAME TO catalog_genres;
ALTER TABLE standard_languages RENAME TO catalog_languages;
ALTER TABLE sys_credentials RENAME TO integration_credentials;
ALTER TABLE system_configs RENAME TO application_settings;
ALTER TABLE unified_video_tags RENAME TO catalog_entry_tags;
ALTER TABLE unified_videos RENAME TO catalog_entries;
ALTER TABLE user_messages RENAME TO member_feedback_posts;
ALTER TABLE video_resolver_sources RENAME TO playback_resolver_profiles;
ALTER TABLE worker_job_audit_events RENAME TO background_job_audit_events;

ALTER TABLE catalog_authority_publications RENAME COLUMN unified_video_id TO catalog_entry_id;
ALTER TABLE catalog_source_match_evidence RENAME COLUMN raw_video_id TO source_record_id;
ALTER TABLE catalog_source_match_evidence RENAME COLUMN unified_video_id TO catalog_entry_id;
ALTER TABLE catalog_resource_discovery_jobs RENAME COLUMN unified_video_id TO catalog_entry_id;
ALTER TABLE catalog_metadata_enrichment_jobs RENAME COLUMN unified_video_id TO catalog_entry_id;
ALTER TABLE collection_plans RENAME COLUMN data_source_id TO collection_source_id;
ALTER TABLE media_image_sources RENAME COLUMN source_domain_id TO source_origin_id;
ALTER TABLE collection_source_origins RENAME COLUMN data_source_id TO collection_source_id;
ALTER TABLE collection_source_origins RENAME COLUMN source_domain_id TO source_origin_id;
ALTER TABLE source_playback_items RENAME COLUMN source_domain_id TO source_origin_id;
ALTER TABLE catalog_release_events RENAME COLUMN unified_video_id TO catalog_entry_id;
ALTER TABLE catalog_external_ids RENAME COLUMN unified_video_id TO catalog_entry_id;
ALTER TABLE provider_ranking_observations RENAME COLUMN unified_video_id TO catalog_entry_id;
ALTER TABLE catalog_magnet_links RENAME COLUMN unified_video_id TO catalog_entry_id;
ALTER TABLE magnet_discovery_jobs RENAME COLUMN unified_video_id TO catalog_entry_id;
ALTER TABLE member_catalog_favorites RENAME COLUMN unified_video_id TO catalog_entry_id;
ALTER TABLE member_playback_progress RENAME COLUMN unified_video_id TO catalog_entry_id;
ALTER TABLE media_request_processing_batch_items RENAME COLUMN media_request_id TO demand_id;
ALTER TABLE source_metadata_provider_runs RENAME COLUMN raw_video_id TO source_record_id;
ALTER TABLE source_record_processing_runs RENAME COLUMN raw_video_id TO source_record_id;
ALTER TABLE catalog_source_bindings RENAME COLUMN raw_video_id TO source_record_id;
ALTER TABLE catalog_source_bindings RENAME COLUMN unified_video_id TO catalog_entry_id;
ALTER TABLE source_records RENAME COLUMN cover_image_id TO media_image_source_id;
ALTER TABLE source_records RENAME COLUMN data_source_id TO collection_source_id;
ALTER TABLE source_origins RENAME COLUMN data_source_id TO collection_source_id;
ALTER TABLE catalog_entry_tags RENAME COLUMN unified_video_id TO catalog_entry_id;
ALTER TABLE catalog_entries RENAME COLUMN cover_image_id TO media_image_source_id;
ALTER VIEW catalog_entry_rankings RENAME COLUMN unified_video_id TO catalog_entry_id;
ALTER VIEW catalog_entry_recent_release_events RENAME COLUMN unified_video_id TO catalog_entry_id;
