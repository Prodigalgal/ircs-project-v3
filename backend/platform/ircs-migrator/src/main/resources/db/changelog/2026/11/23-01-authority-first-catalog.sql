-- liquibase formatted sql

-- changeset codex:1795380000001-authority-first-catalog
-- Business version: 4.0.0. Keep the existing public video projection and adopt the new pipeline in an isolated database.
CREATE TABLE authority_catalog_works (
    id uuid PRIMARY KEY,
    media_type varchar(8) NOT NULL CHECK (media_type IN ('movie', 'tv')),
    tmdb_id bigint NOT NULL CHECK (tmdb_id > 0),
    title varchar(500) NOT NULL CHECK (btrim(title) <> ''),
    original_title varchar(500),
    title_key varchar(500) NOT NULL,
    original_title_key varchar(500),
    release_year smallint CHECK (release_year BETWEEN 1800 AND 2199),
    released_on date,
    imdb_id varchar(32) CHECK (imdb_id ~ '^tt[1-9][0-9]*$' OR imdb_id ~ '^tt0+[1-9][0-9]*$'),
    regular_season_count integer NOT NULL DEFAULT 0 CHECK (regular_season_count >= 0),
    last_verified_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (media_type, tmdb_id),
    UNIQUE (id, media_type)
);
CREATE INDEX authority_catalog_title_year_idx ON authority_catalog_works (title_key, release_year, media_type);
CREATE INDEX authority_catalog_original_year_idx ON authority_catalog_works (original_title_key, release_year, media_type);
CREATE INDEX authority_catalog_imdb_idx ON authority_catalog_works (imdb_id, media_type) WHERE imdb_id IS NOT NULL;

CREATE TABLE authority_catalog_seasons (
    work_id uuid NOT NULL REFERENCES authority_catalog_works(id) ON DELETE CASCADE,
    season_number integer NOT NULL CHECK (season_number >= 0),
    tmdb_season_id bigint NOT NULL CHECK (tmdb_season_id > 0),
    title varchar(500),
    released_on date,
    verified_at timestamptz,
    PRIMARY KEY (work_id, season_number),
    UNIQUE (tmdb_season_id)
);
CREATE TABLE authority_catalog_episodes (
    work_id uuid NOT NULL,
    season_number integer NOT NULL,
    episode_number integer NOT NULL CHECK (episode_number > 0),
    tmdb_episode_id bigint NOT NULL UNIQUE CHECK (tmdb_episode_id > 0),
    title varchar(500),
    aired_on date,
    PRIMARY KEY (work_id, season_number, episode_number),
    FOREIGN KEY (work_id, season_number) REFERENCES authority_catalog_seasons (work_id, season_number) ON DELETE CASCADE
);

CREATE TABLE authority_catalog_projections (
    work_id uuid NOT NULL,
    media_type varchar(8) NOT NULL,
    season_number integer NOT NULL,
    verified_season_number integer GENERATED ALWAYS AS (nullif(season_number, -1)) STORED,
    unified_video_id uuid NOT NULL UNIQUE REFERENCES unified_videos(id) ON DELETE CASCADE,
    index_generation bigint NOT NULL DEFAULT 1,
    index_pending boolean NOT NULL DEFAULT true,
    PRIMARY KEY (work_id, season_number),
    CHECK ((media_type='movie' AND season_number=-1) OR (media_type='tv' AND season_number>=0)),
    FOREIGN KEY (work_id, media_type) REFERENCES authority_catalog_works (id, media_type) ON DELETE CASCADE,
    FOREIGN KEY (work_id, verified_season_number) REFERENCES authority_catalog_seasons (work_id, season_number) ON DELETE CASCADE
);
CREATE INDEX authority_catalog_pending_index_idx ON authority_catalog_projections (unified_video_id) WHERE index_pending;

CREATE TABLE authority_resource_bindings (
    raw_video_id uuid PRIMARY KEY REFERENCES raw_videos(id) ON DELETE CASCADE,
    unified_video_id uuid NOT NULL REFERENCES authority_catalog_projections(unified_video_id) ON DELETE CASCADE,
    evidence_kind varchar(40) NOT NULL,
    bound_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX authority_resource_bindings_projection_idx ON authority_resource_bindings (unified_video_id);

CREATE TABLE authority_catalog_imports (
    media_type varchar(8) NOT NULL CHECK (media_type IN ('movie', 'tv')),
    tmdb_id bigint NOT NULL CHECK (tmdb_id > 0),
    season_number integer NOT NULL DEFAULT -1 CHECK (season_number BETWEEN -1 AND 1000),
    priority integer NOT NULL DEFAULT 0 CHECK (priority >= 0),
    status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DISPATCHING', 'RETRY', 'SUCCESS', 'UNAVAILABLE')),
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    lease_token uuid,
    lease_until timestamptz,
    attempts integer NOT NULL DEFAULT 0,
    last_error varchar(120),
    last_success_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (media_type, tmdb_id, season_number),
    CHECK (media_type <> 'movie' OR season_number = -1),
    CHECK ((status = 'DISPATCHING') = (lease_token IS NOT NULL AND lease_until IS NOT NULL))
);
CREATE INDEX authority_catalog_import_due_idx ON authority_catalog_imports (priority DESC, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'RETRY');
CREATE INDEX authority_catalog_import_lease_idx ON authority_catalog_imports (lease_until)
    WHERE status = 'DISPATCHING';

CREATE TABLE authority_tmdb_changes_cursor (
    media_type varchar(8) PRIMARY KEY CHECK (media_type IN ('movie', 'tv')),
    change_day date NOT NULL,
    next_page integer NOT NULL DEFAULT 1 CHECK (next_page > 0),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- rollback DROP TABLE authority_tmdb_changes_cursor, authority_catalog_imports, authority_resource_bindings, authority_catalog_projections, authority_catalog_episodes, authority_catalog_seasons, authority_catalog_works;
