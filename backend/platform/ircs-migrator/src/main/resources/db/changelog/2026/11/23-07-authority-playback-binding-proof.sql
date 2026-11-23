-- liquibase formatted sql

-- changeset codex:1795380100003-authority-playback-binding-proof
ALTER FUNCTION catalog_list_playable_episodes(uuid) RENAME TO catalog_list_scope_matched_episodes;
ALTER FUNCTION catalog_has_target_resources(uuid, integer, integer) RENAME TO catalog_previous_has_target_resources;
ALTER FUNCTION catalog_resource_inventory(uuid, integer) RENAME TO catalog_previous_resource_inventory;

CREATE FUNCTION catalog_list_playable_episodes(target_video_id uuid)
RETURNS TABLE(
    source_id uuid,
    source_name text,
    playlist_name text,
    episode_id uuid,
    episode_name text,
    episode_url text,
    episode_domain_id uuid,
    season_number integer,
    episode_number integer
)
LANGUAGE sql STABLE PARALLEL SAFE AS $$
    SELECT matched.*
      FROM catalog_list_scope_matched_episodes(target_video_id) matched
     WHERE NOT EXISTS (
         SELECT 1 FROM authority_catalog_projections projection
          WHERE projection.unified_video_id = target_video_id
     ) OR EXISTS (
         SELECT 1 FROM authority_resource_bindings evidence
          WHERE evidence.raw_video_id = matched.source_id AND evidence.unified_video_id = target_video_id
     )
$$;

CREATE FUNCTION catalog_resource_inventory(target_video_id uuid, required_season integer)
RETURNS TABLE(resource_available boolean, available_episode integer)
LANGUAGE sql STABLE PARALLEL SAFE AS $$
    WITH authority_entry AS MATERIALIZED (
        SELECT projection.unified_video_id, projection.media_type, entry.adult_restricted, entry.adult_assessment
          FROM authority_catalog_projections projection
          JOIN unified_videos entry ON entry.id = projection.unified_video_id
         WHERE projection.unified_video_id = target_video_id
    )
    SELECT inventory.resource_available, inventory.available_episode
      FROM authority_entry entry
      CROSS JOIN LATERAL (
          SELECT count(*) > 0 AS resource_available,
                 max(coalesce(playback.episode_number, catalog_episode_number(playback.episode_name))) AS available_episode
            FROM catalog_list_playable_episodes(entry.unified_video_id) playback
           WHERE NOT coalesce(entry.adult_restricted, false)
             AND lower(coalesce(entry.adult_assessment ->> 'adultRestricted', 'false')) = 'false'
             AND upper(coalesce(entry.adult_assessment ->> 'level', 'SAFE')) <> 'ADULT'
             AND (required_season = 0 OR (entry.media_type = 'tv' AND playback.season_number = required_season))
      ) inventory
    UNION ALL
    SELECT legacy.resource_available, legacy.available_episode
      FROM catalog_legacy_resource_inventory(target_video_id, required_season) legacy
     WHERE NOT EXISTS (SELECT 1 FROM authority_entry)
$$;

CREATE FUNCTION catalog_has_target_resources(target_video_id uuid, required_season integer, required_episode integer)
RETURNS boolean
LANGUAGE sql STABLE PARALLEL SAFE AS $$
    SELECT coalesce((
        SELECT NOT coalesce(entry.adult_restricted, false)
           AND lower(coalesce(entry.adult_assessment ->> 'adultRestricted', 'false')) = 'false'
           AND upper(coalesce(entry.adult_assessment ->> 'level', 'SAFE')) <> 'ADULT'
           AND (
               EXISTS (
                   SELECT 1 FROM catalog_list_playable_episodes(entry.id) playback
                    WHERE (required_season = 0 OR (projection.media_type = 'tv' AND playback.season_number = required_season))
                      AND (required_episode = 0 OR coalesce(
                          playback.episode_number, catalog_episode_number(playback.episode_name)) = required_episode)
               )
               OR (
                   projection.media_type = 'movie' AND required_season = 0 AND required_episode = 0
                   AND entry.content_visibility = 'PUBLIC'
                   AND EXISTS (SELECT 1 FROM magnet_links link WHERE link.unified_video_id = entry.id AND link.status = 'APPROVED')
               )
           )
          FROM authority_catalog_projections projection
          JOIN unified_videos entry ON entry.id = projection.unified_video_id
         WHERE projection.unified_video_id = target_video_id
    ), catalog_legacy_has_target_resources(target_video_id, required_season, required_episode))
$$;

-- rollback DROP FUNCTION catalog_has_target_resources(uuid, integer, integer);
-- rollback DROP FUNCTION catalog_resource_inventory(uuid, integer);
-- rollback DROP FUNCTION catalog_list_playable_episodes(uuid);
-- rollback ALTER FUNCTION catalog_list_scope_matched_episodes(uuid) RENAME TO catalog_list_playable_episodes;
-- rollback ALTER FUNCTION catalog_previous_has_target_resources(uuid, integer, integer) RENAME TO catalog_has_target_resources;
-- rollback ALTER FUNCTION catalog_previous_resource_inventory(uuid, integer) RENAME TO catalog_resource_inventory;
