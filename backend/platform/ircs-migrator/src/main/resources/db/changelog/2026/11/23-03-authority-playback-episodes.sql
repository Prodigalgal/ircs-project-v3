-- liquibase formatted sql

-- changeset codex:1795380000005-authority-playback-episodes
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
    SELECT rv.id, ds.name::text, pl.name::text, ep.id, ep.name::text, ep.url::text,
           ep.source_domain_id, p.season_number, verified_episode.episode_number
      FROM raw_video_unified_video link
      JOIN raw_videos rv ON rv.id = link.raw_video_id
      JOIN playlists pl ON pl.video_id = rv.id
      JOIN episodes ep ON ep.playlist_id = pl.id
      LEFT JOIN data_sources ds ON ds.id = rv.data_source_id
      LEFT JOIN authority_catalog_projections p ON p.unified_video_id = link.unified_video_id
      LEFT JOIN authority_catalog_seasons verified_season
        ON verified_season.work_id = p.work_id AND verified_season.season_number = p.season_number
       AND verified_season.verified_at IS NOT NULL
      LEFT JOIN authority_resource_bindings binding
        ON rv.season IS NULL AND binding.raw_video_id = rv.id AND binding.unified_video_id = p.unified_video_id
      CROSS JOIN LATERAL (
          SELECT CASE WHEN p.media_type = 'tv' THEN regexp_match(btrim(ep.name), '^S([0-9]{1,4})E([0-9]{1,4})$', 'i')
                 END AS season_and_episode
      ) label
      LEFT JOIN authority_catalog_episodes verified_episode
        ON verified_episode.work_id = verified_season.work_id
       AND verified_episode.season_number = verified_season.season_number
       AND verified_episode.episode_number = coalesce(label.season_and_episode[2]::integer, catalog_episode_number(ep.name))
     WHERE link.unified_video_id = target_video_id
       AND (p.work_id IS NULL OR (
           (btrim(ep.url) ~* '^https?://[^[:space:]]+$'
            OR (btrim(ep.url) ~ '^/[^[:space:]]+$' AND ep.source_domain_id IS NOT NULL))
           AND NOT coalesce(ds.adult_restricted, false)
           AND (p.media_type = 'movie' OR (
               verified_episode.episode_number IS NOT NULL
               AND (rv.season = p.season_number OR (rv.season IS NULL
                    AND (binding.raw_video_id IS NOT NULL OR label.season_and_episode[1]::integer = p.season_number)))
               AND (label.season_and_episode IS NULL OR label.season_and_episode[1]::integer = p.season_number)
           ))
       ))
$$;

ALTER FUNCTION catalog_has_target_resources(uuid, integer, integer)
    RENAME TO catalog_legacy_has_target_resources;
ALTER FUNCTION catalog_resource_inventory(uuid, integer)
    RENAME TO catalog_legacy_resource_inventory;

CREATE FUNCTION catalog_has_target_resources(
    target_video_id uuid,
    required_season integer,
    required_episode integer
) RETURNS boolean
LANGUAGE sql STABLE PARALLEL SAFE AS $$
    SELECT CASE WHEN EXISTS (
        SELECT 1 FROM authority_catalog_projections WHERE unified_video_id = target_video_id AND media_type = 'tv'
    ) THEN EXISTS (
        SELECT 1
          FROM unified_videos uv
          CROSS JOIN LATERAL catalog_list_playable_episodes(uv.id) playback
         WHERE uv.id = target_video_id
           AND NOT coalesce(uv.adult_restricted, false)
           AND lower(coalesce(uv.adult_assessment ->> 'adultRestricted', 'false')) = 'false'
           AND upper(coalesce(uv.adult_assessment ->> 'level', 'SAFE')) <> 'ADULT'
           AND (required_season = 0 OR playback.season_number = required_season)
           AND (required_episode = 0 OR playback.episode_number = required_episode)
    ) ELSE catalog_legacy_has_target_resources(target_video_id, required_season, required_episode) END
$$;

CREATE FUNCTION catalog_resource_inventory(target_video_id uuid, required_season integer)
RETURNS TABLE(resource_available boolean, available_episode integer)
LANGUAGE sql STABLE PARALLEL SAFE AS $$
    WITH authority_tv AS MATERIALIZED (
        SELECT uv.id, uv.adult_restricted, uv.adult_assessment
          FROM authority_catalog_projections p JOIN unified_videos uv ON uv.id = p.unified_video_id
         WHERE p.unified_video_id = target_video_id AND p.media_type = 'tv'
    )
    SELECT playback.resource_available, playback.available_episode
      FROM authority_tv tv
      CROSS JOIN LATERAL (
          SELECT count(*) > 0 AS resource_available, max(e.episode_number) AS available_episode
            FROM catalog_list_playable_episodes(tv.id) e
           WHERE NOT coalesce(tv.adult_restricted, false)
             AND lower(coalesce(tv.adult_assessment ->> 'adultRestricted', 'false')) = 'false'
             AND upper(coalesce(tv.adult_assessment ->> 'level', 'SAFE')) <> 'ADULT'
             AND (required_season = 0 OR e.season_number = required_season)
      ) playback
    UNION ALL
    SELECT legacy.resource_available, legacy.available_episode
      FROM catalog_legacy_resource_inventory(target_video_id, required_season) legacy
     WHERE NOT EXISTS (SELECT 1 FROM authority_tv)
$$;

-- rollback DROP FUNCTION catalog_resource_inventory(uuid, integer);
-- rollback DROP FUNCTION catalog_has_target_resources(uuid, integer, integer);
-- rollback ALTER FUNCTION catalog_legacy_resource_inventory(uuid, integer) RENAME TO catalog_resource_inventory;
-- rollback ALTER FUNCTION catalog_legacy_has_target_resources(uuid, integer, integer) RENAME TO catalog_has_target_resources;
-- rollback DROP FUNCTION catalog_list_playable_episodes(uuid);
