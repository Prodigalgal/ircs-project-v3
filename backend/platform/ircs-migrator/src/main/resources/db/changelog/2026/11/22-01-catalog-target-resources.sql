-- liquibase formatted sql

-- changeset codex:20261122-catalog-target-resources
-- Business version 3.12.1. Keep catalog_resource_inventory unchanged for existing consumers.
CREATE FUNCTION catalog_has_target_resources(
    target_video_id uuid,
    required_season integer,
    required_episode integer
) RETURNS boolean
LANGUAGE sql STABLE PARALLEL SAFE AS $$
    SELECT EXISTS (
        SELECT 1
          FROM raw_video_unified_video rvu
          JOIN raw_videos rv ON rv.id = rvu.raw_video_id
          JOIN unified_videos uv ON uv.id = rvu.unified_video_id
          LEFT JOIN data_sources ds ON ds.id = rv.data_source_id
          JOIN playlists pl ON pl.video_id = rv.id
          JOIN episodes ep ON ep.playlist_id = pl.id
         WHERE rvu.unified_video_id = target_video_id
           AND (btrim(ep.url) ~* '^https?://[^[:space:]]+$'
                OR (btrim(ep.url) ~ '^/[^[:space:]]+$' AND ep.source_domain_id IS NOT NULL))
           AND coalesce(ds.adult_restricted, false) = false
           AND coalesce(uv.adult_restricted, false) = false
           AND lower(coalesce(uv.adult_assessment ->> 'adultRestricted', 'false')) = 'false'
           AND upper(coalesce(uv.adult_assessment ->> 'level', 'SAFE')) <> 'ADULT'
           AND (required_season = 0 OR rv.season = required_season
                OR (rv.season IS NULL AND uv.season = required_season))
           AND (required_season = 0 OR btrim(ep.name) !~* '^S[0-9]{1,2}E[0-9]{1,3}$'
                OR substring(upper(btrim(ep.name)) FROM '^S([0-9]{1,2})E')::integer = required_season)
           AND (required_episode = 0 OR catalog_episode_number(ep.name) = required_episode)
    ) OR (
        required_season = 0 AND required_episode = 0
        AND EXISTS (
            SELECT 1
              FROM unified_videos uv
              JOIN magnet_links ml ON ml.unified_video_id = uv.id
             WHERE uv.id = target_video_id
               AND ml.status = 'APPROVED'
               AND uv.content_visibility = 'PUBLIC'
               AND coalesce(uv.adult_restricted, false) = false
               AND lower(coalesce(uv.adult_assessment ->> 'adultRestricted', 'false')) = 'false'
               AND upper(coalesce(uv.adult_assessment ->> 'level', 'SAFE')) <> 'ADULT'
        )
    )
$$;
-- rollback DROP FUNCTION catalog_has_target_resources(uuid, integer, integer);
