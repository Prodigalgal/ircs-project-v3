--liquibase formatted sql

--changeset codex:1795470000002-canonical-catalog
--comment: Establish shared work, publication scope and canonical season and episode identities.
ALTER TABLE catalog_works ALTER COLUMN tmdb_id DROP NOT NULL,
    ALTER COLUMN last_verified_at DROP NOT NULL,
    ALTER COLUMN last_verified_at DROP DEFAULT,
    ADD COLUMN identity_status varchar(16) NOT NULL DEFAULT 'VERIFIED',
    ADD COLUMN category_id uuid REFERENCES catalog_categories(id),
    ADD CONSTRAINT catalog_work_identity_status_check
        CHECK (identity_status IN ('VERIFIED', 'PROVISIONAL')),
    ADD CONSTRAINT catalog_work_verification_check
        CHECK (identity_status <> 'VERIFIED' OR (tmdb_id IS NOT NULL AND last_verified_at IS NOT NULL));

ALTER TABLE catalog_seasons ADD COLUMN id uuid NOT NULL DEFAULT uuidv7(),
    ALTER COLUMN tmdb_season_id DROP NOT NULL,
    ADD CONSTRAINT catalog_season_id_unique UNIQUE (id),
    ADD CONSTRAINT catalog_season_work_scope UNIQUE (work_id, id),
    ADD CONSTRAINT catalog_season_number_scope UNIQUE (work_id, id, season_number);

ALTER TABLE catalog_episodes ADD COLUMN id uuid NOT NULL DEFAULT uuidv7(),
    ADD COLUMN season_id uuid,
    ALTER COLUMN tmdb_episode_id DROP NOT NULL,
    ADD CONSTRAINT catalog_episode_id_unique UNIQUE (id),
    ADD CONSTRAINT catalog_episode_work_scope UNIQUE (work_id, id),
    ADD CONSTRAINT catalog_episode_season_scope UNIQUE (work_id, season_id, id);
UPDATE catalog_episodes ep SET season_id = s.id
FROM catalog_seasons s WHERE s.work_id = ep.work_id AND s.season_number = ep.season_number;
ALTER TABLE catalog_person_credits
    DROP CONSTRAINT catalog_credits_work_id_season_number_episode_number_fkey;
ALTER TABLE catalog_episodes
    DROP CONSTRAINT authority_catalog_episodes_pkey,
    DROP CONSTRAINT authority_catalog_episodes_work_id_season_number_fkey,
    ALTER COLUMN season_number DROP NOT NULL,
    ADD CONSTRAINT catalog_episodes_pkey PRIMARY KEY (id),
    ADD CONSTRAINT catalog_episode_number_unique UNIQUE NULLS NOT DISTINCT (work_id, season_number, episode_number),
    ADD CONSTRAINT catalog_episode_work_fkey FOREIGN KEY (work_id) REFERENCES catalog_works(id) ON DELETE CASCADE,
    ADD CONSTRAINT catalog_episode_number_scope_fkey FOREIGN KEY (work_id, season_id, season_number)
        REFERENCES catalog_seasons(work_id, id, season_number) ON DELETE CASCADE,
    ADD CONSTRAINT catalog_episode_scope_check CHECK ((season_id IS NULL) = (season_number IS NULL));
ALTER TABLE catalog_episodes DROP CONSTRAINT catalog_episode_id_unique;
ALTER TABLE catalog_person_credits
    ADD COLUMN season_id uuid,
    ADD COLUMN episode_id uuid,
    ADD CONSTRAINT catalog_credit_episode_number_fkey
        FOREIGN KEY (work_id, season_number, episode_number)
        REFERENCES catalog_episodes(work_id, season_number, episode_number) ON DELETE CASCADE,
    ADD CONSTRAINT catalog_credit_season_id_fkey FOREIGN KEY (work_id, season_id)
        REFERENCES catalog_seasons(work_id, id) ON DELETE CASCADE,
    ADD CONSTRAINT catalog_credit_episode_scope_fkey FOREIGN KEY (work_id, season_id, episode_id)
        REFERENCES catalog_episodes(work_id, season_id, id) ON DELETE CASCADE;

ALTER TABLE catalog_entries ADD COLUMN work_id uuid,
    ADD COLUMN season_id uuid,
    ADD CONSTRAINT catalog_entry_work_fkey FOREIGN KEY (work_id) REFERENCES catalog_works(id) ON DELETE CASCADE,
    ADD CONSTRAINT catalog_entry_season_fkey FOREIGN KEY (work_id, season_id)
        REFERENCES catalog_seasons(work_id, id) ON DELETE CASCADE,
    ADD CONSTRAINT catalog_entry_publication_scope UNIQUE NULLS NOT DISTINCT (work_id, season_id),
    ADD CONSTRAINT catalog_entry_work_scope UNIQUE (work_id, id);

UPDATE catalog_entries entry SET work_id = publication.work_id, season_id = season.id
FROM catalog_authority_publications publication
LEFT JOIN catalog_seasons season
    ON season.work_id = publication.work_id AND season.season_number = publication.season_number
WHERE publication.catalog_entry_id = entry.id;
ALTER TABLE catalog_entries ALTER COLUMN work_id SET NOT NULL;

DROP TABLE catalog_source_match_evidence;
DROP TABLE catalog_authority_publications;
DROP TABLE catalog_external_ids;

CREATE TABLE catalog_work_titles (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    work_id uuid NOT NULL REFERENCES catalog_works(id) ON DELETE CASCADE,
    language_tag varchar(35) NOT NULL DEFAULT 'und',
    title_kind varchar(16) NOT NULL CHECK (title_kind IN ('PRIMARY', 'ORIGINAL', 'ALIAS')),
    title varchar(500) NOT NULL CHECK (btrim(title) <> ''),
    title_key varchar(500) NOT NULL CHECK (btrim(title_key) <> ''),
    provider varchar(32) NOT NULL,
    observed_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (work_id, provider, language_tag, title_kind, title_key)
);
CREATE INDEX catalog_work_title_lookup ON catalog_work_titles (title_key, work_id);

CREATE TABLE catalog_external_ids (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    provider varchar(32) NOT NULL CHECK (btrim(provider) <> ''),
    media_type varchar(16) NOT NULL CHECK (media_type IN ('movie', 'tv')),
    external_id varchar(255) NOT NULL CHECK (
        btrim(external_id) = external_id AND external_id <> '' AND external_id !~ '^(0+|tt0+)$'),
    target_kind varchar(8) NOT NULL CHECK (target_kind IN ('WORK', 'SEASON', 'EPISODE')),
    work_id uuid NOT NULL REFERENCES catalog_works(id) ON DELETE CASCADE,
    season_id uuid,
    episode_id uuid,
    observed_at timestamptz NOT NULL DEFAULT now(),
    verified_at timestamptz,
    UNIQUE (provider, media_type, target_kind, external_id),
    FOREIGN KEY (work_id, season_id) REFERENCES catalog_seasons(work_id, id) ON DELETE CASCADE,
    FOREIGN KEY (work_id, episode_id) REFERENCES catalog_episodes(work_id, id) ON DELETE CASCADE,
    FOREIGN KEY (work_id, season_id, episode_id) REFERENCES catalog_episodes(work_id, season_id, id) ON DELETE CASCADE,
    CHECK (
        (target_kind = 'WORK' AND season_id IS NULL AND episode_id IS NULL)
        OR (target_kind = 'SEASON' AND season_id IS NOT NULL AND episode_id IS NULL)
        OR (target_kind = 'EPISODE' AND season_id IS NULL AND episode_id IS NOT NULL)
    )
);
CREATE INDEX catalog_external_ids_by_work ON catalog_external_ids (work_id);

CREATE TABLE catalog_metadata_observations (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    work_id uuid NOT NULL REFERENCES catalog_works(id) ON DELETE CASCADE,
    season_id uuid,
    provider varchar(32) NOT NULL,
    external_id varchar(255) NOT NULL,
    observed_at timestamptz NOT NULL DEFAULT now(),
    facts jsonb NOT NULL CHECK (jsonb_typeof(facts) = 'object'),
    FOREIGN KEY (work_id, season_id) REFERENCES catalog_seasons(work_id, id) ON DELETE CASCADE
);
CREATE INDEX catalog_metadata_observation_subject ON catalog_metadata_observations (work_id, observed_at DESC);

CREATE TABLE catalog_rating_observations (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    work_id uuid NOT NULL REFERENCES catalog_works(id) ON DELETE CASCADE,
    season_id uuid,
    provider varchar(32) NOT NULL,
    rating numeric(4,2) NOT NULL CHECK (rating BETWEEN 0 AND 10),
    vote_count bigint CHECK (vote_count >= 0),
    observed_on date NOT NULL,
    UNIQUE NULLS NOT DISTINCT (work_id, season_id, provider, observed_on),
    FOREIGN KEY (work_id, season_id) REFERENCES catalog_seasons(work_id, id) ON DELETE CASCADE
);

CREATE TABLE catalog_content_assessments (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    catalog_entry_id uuid NOT NULL REFERENCES catalog_entries(id) ON DELETE CASCADE,
    policy_version varchar(32) NOT NULL,
    assessment jsonb NOT NULL CHECK (jsonb_typeof(assessment) = 'object'),
    restricted boolean NOT NULL,
    assessed_at timestamptz NOT NULL,
    UNIQUE (catalog_entry_id, policy_version, assessed_at)
);

CREATE TABLE catalog_work_genres (
    work_id uuid NOT NULL REFERENCES catalog_works(id) ON DELETE CASCADE,
    genre_id uuid NOT NULL REFERENCES catalog_genres(id),
    PRIMARY KEY (work_id, genre_id)
);
CREATE TABLE catalog_work_regions (
    work_id uuid NOT NULL REFERENCES catalog_works(id) ON DELETE CASCADE,
    region_id uuid NOT NULL REFERENCES catalog_regions(id),
    PRIMARY KEY (work_id, region_id)
);
CREATE TABLE catalog_work_languages (
    work_id uuid NOT NULL REFERENCES catalog_works(id) ON DELETE CASCADE,
    language_id uuid NOT NULL REFERENCES catalog_languages(id),
    PRIMARY KEY (work_id, language_id)
);
