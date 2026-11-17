-- liquibase formatted sql
-- Business version: 3.7.1. Additive only; rollback application images without dropping catalog data.
-- changeset codex:1785900000007-global-rankings-and-discovery
CREATE TABLE external_catalog_identities (
    provider varchar(32) NOT NULL,
    media_type varchar(16) NOT NULL CHECK (media_type IN ('movie', 'tv')),
    external_id varchar(255) NOT NULL,
    season integer NOT NULL DEFAULT 0 CHECK (season >= 0),
    unified_video_id uuid NOT NULL REFERENCES unified_videos(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (provider, media_type, external_id, season)
);
CREATE INDEX external_catalog_identities_video_idx ON external_catalog_identities(unified_video_id);

CREATE TABLE external_trend_boards (
    provider varchar(32) NOT NULL,
    board varchar(64) NOT NULL,
    media_type varchar(16) NOT NULL,
    source_window varchar(24) NOT NULL,
    last_attempt_at timestamptz NOT NULL,
    last_success_at timestamptz,
    status varchar(24) NOT NULL,
    error_code varchar(120),
    item_count integer NOT NULL DEFAULT 0,
    PRIMARY KEY (provider, board, media_type, source_window)
);

CREATE TABLE external_trend_observations (
    provider varchar(32) NOT NULL,
    board varchar(64) NOT NULL,
    media_type varchar(16) NOT NULL,
    source_window varchar(24) NOT NULL,
    external_id varchar(255) NOT NULL,
    season integer NOT NULL DEFAULT 0,
    unified_video_id uuid NOT NULL REFERENCES unified_videos(id) ON DELETE CASCADE,
    position integer CHECK (position BETWEEN 1 AND 1000),
    previous_position integer,
    popularity numeric,
    vote_count bigint,
    observed_at timestamptz NOT NULL,
    event_date date,
    event_type varchar(32),
    episode integer,
    region varchar(32),
    source_url varchar(1000),
    PRIMARY KEY (provider, board, media_type, source_window, external_id, season),
    CHECK (source_window = 'event' OR position IS NOT NULL)
);
CREATE INDEX external_trend_observations_video_idx ON external_trend_observations(unified_video_id);
CREATE INDEX external_trend_observations_event_idx ON external_trend_observations(event_date DESC)
    WHERE event_date IS NOT NULL;

-- Event history survives rotation of a daily/weekly board; yesterday's broadcast is still a real update.
CREATE TABLE external_catalog_events (
    unified_video_id uuid NOT NULL REFERENCES unified_videos(id) ON DELETE CASCADE,
    provider varchar(32) NOT NULL,
    external_id varchar(255) NOT NULL,
    season integer NOT NULL DEFAULT 0,
    episode integer NOT NULL DEFAULT 0,
    event_date date NOT NULL,
    event_type varchar(32) NOT NULL,
    observed_at timestamptz NOT NULL,
    source_url varchar(1000),
    region varchar(32),
    PRIMARY KEY (unified_video_id, provider, season, episode, event_date, event_type)
);
CREATE INDEX external_catalog_events_date_idx ON external_catalog_events(event_date DESC, unified_video_id);
CREATE VIEW external_catalog_recent_events AS
SELECT DISTINCT ON (unified_video_id) unified_video_id, provider, event_date, event_type,
       season, nullif(episode, 0) AS episode, observed_at, source_url, region
  FROM external_catalog_events
 WHERE event_date BETWEEN CURRENT_DATE - 90 AND CURRENT_DATE
 ORDER BY unified_video_id, event_date DESC, season DESC, episode DESC, observed_at DESC, provider;

CREATE TABLE catalog_discovery_requests (
    unified_video_id uuid NOT NULL REFERENCES unified_videos(id) ON DELETE CASCADE,
    target_season integer NOT NULL DEFAULT 0 CHECK (target_season >= 0),
    target_episode integer NOT NULL DEFAULT 0 CHECK (target_episode >= 0),
    keyword varchar(255) NOT NULL,
    priority numeric NOT NULL DEFAULT 0,
    status varchar(24) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'DISPATCHING', 'SUBMITTED', 'RETRY')),
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    lease_token uuid,
    lease_until timestamptz,
    last_error varchar(120),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    submitted_at timestamptz,
    PRIMARY KEY (unified_video_id, target_season, target_episode)
);
CREATE INDEX catalog_discovery_requests_due_idx
    ON catalog_discovery_requests(status, next_attempt_at, priority DESC);

-- One contribution per provider: day+week from the same source cannot double its weight.
-- A failed source keeps its last successful sample, with a visible age and a seven-day hard expiry.
CREATE VIEW external_catalog_rankings AS
WITH per_provider AS (
    SELECT unified_video_id, provider,
           max(CASE WHEN source_window IN ('day', 'week', 'chart', 'popular') THEN
               (1.0 / (20 + position))
               * CASE WHEN source_window = 'week' THEN 0.65 ELSE 1 END
               * (1 + least(0.20, greatest(0, coalesce(previous_position, position) - position) / 100.0))
               * exp(-extract(epoch FROM (now() - observed_at)) / 172800.0)
               ELSE 0 END) AS provider_heat,
           max(observed_at) AS observed_at,
           max(observed_at) FILTER (WHERE source_window IN ('day', 'week', 'chart', 'popular')) AS trend_observed_at,
           max(event_date) FILTER (WHERE event_date <= CURRENT_DATE) AS event_date
      FROM external_trend_observations
     WHERE observed_at >= now() - interval '7 days'
       AND observed_at <= now() + interval '5 minutes'
     GROUP BY unified_video_id, provider
)
SELECT unified_video_id,
       sum(provider_heat) * (1 + least(0.30, greatest(0, count(*) FILTER (WHERE provider_heat > 0) - 1) * 0.10)) AS heat,
       string_agg(provider, ',' ORDER BY provider) AS sources,
       coalesce(max(trend_observed_at), max(observed_at)) AS observed_at,
       max(event_date) AS event_date
  FROM per_provider
 GROUP BY unified_video_id;

-- Only unambiguous episode labels are interpreted; counts of mirrors/playlists are not episode numbers.
CREATE FUNCTION catalog_episode_number(label text) RETURNS integer
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT CASE
        WHEN btrim(label) ~* '^(第)?[0-9]{1,4}(集|话|期)$'
            THEN substring(btrim(label) FROM '[0-9]{1,4}')::integer
        WHEN btrim(label) ~* '^(EP[ ._-]*)?[0-9]{1,3}$'
            THEN substring(btrim(label) FROM '[0-9]{1,3}')::integer
        WHEN btrim(label) ~* '^S[0-9]{1,2}E[0-9]{1,3}$'
            THEN substring(upper(btrim(label)) FROM 'E([0-9]{1,3})$')::integer
        ELSE NULL
    END
$$;

-- Shared inventory semantics for catalog ingestion, discovery and portal badges.
-- This proves that a resource is recorded, not that a remote stream has passed a live playback probe.
CREATE FUNCTION catalog_resource_inventory(target_video_id uuid, required_season integer)
RETURNS TABLE(resource_available boolean, available_episode integer)
LANGUAGE sql STABLE PARALLEL SAFE AS $$
    SELECT count(*) > 0, max(catalog_episode_number(ep.name))
      FROM raw_video_unified_video rvu
      JOIN raw_videos rv ON rv.id = rvu.raw_video_id
      JOIN playlists pl ON pl.video_id = rv.id
      JOIN episodes ep ON ep.playlist_id = pl.id
     WHERE rvu.unified_video_id = target_video_id
       AND (btrim(ep.url) ~* '^https?://[^[:space:]]+$'
            OR (btrim(ep.url) ~ '^/[^[:space:]]+$' AND ep.source_domain_id IS NOT NULL))
       AND coalesce(rv.adult_restricted, false) = false
       AND lower(coalesce(rv.adult_assessment ->> 'adultRestricted', 'false')) = 'false'
       AND upper(coalesce(rv.adult_assessment ->> 'level', 'SAFE')) <> 'ADULT'
       AND (required_season = 0 OR rv.season = required_season OR (rv.season IS NULL AND EXISTS (
            SELECT 1 FROM unified_videos uv WHERE uv.id = target_video_id AND uv.season = required_season)))
       AND (required_season = 0 OR btrim(ep.name) !~* '^S[0-9]{1,2}E[0-9]{1,3}$'
            OR substring(upper(btrim(ep.name)) FROM '^S([0-9]{1,2})E')::integer = required_season)
$$;

-- Preserve the existing content tables, source evidence, bindings, user data and manual locks.
-- rollback SELECT 1;

-- Independent metadata enrichment for catalog entries without any collected raw resource.
CREATE TABLE catalog_metadata_requests (
    unified_video_id uuid PRIMARY KEY REFERENCES unified_videos(id) ON DELETE CASCADE,
    provider varchar(32) NOT NULL,
    media_type varchar(16) NOT NULL CHECK (media_type IN ('movie', 'tv')),
    external_id varchar(255) NOT NULL,
    season integer NOT NULL DEFAULT 0 CHECK (season >= 0),
    status varchar(24) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'DISPATCHING', 'SUCCESS', 'RETRY', 'UNAVAILABLE')),
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    lease_token uuid,
    lease_until timestamptz,
    last_error varchar(120),
    last_attempt_at timestamptz,
    last_success_at timestamptz,
    index_pending boolean NOT NULL DEFAULT false,
    index_generation bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX catalog_metadata_requests_due_idx ON catalog_metadata_requests(status, next_attempt_at);
CREATE INDEX catalog_metadata_requests_index_pending_idx ON catalog_metadata_requests(updated_at)
    WHERE index_pending;
