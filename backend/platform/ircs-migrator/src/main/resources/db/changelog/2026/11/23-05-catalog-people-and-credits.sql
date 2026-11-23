-- liquibase formatted sql

-- changeset codex:1795380100001-catalog-people-and-credits
CREATE TABLE people (
    id uuid PRIMARY KEY,
    display_name varchar(255) NOT NULL CHECK (btrim(display_name) <> ''),
    original_name varchar(255),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE person_external_ids (
    provider varchar(24) NOT NULL CHECK (btrim(provider) <> ''),
    external_id varchar(128) NOT NULL CHECK (btrim(external_id) <> ''),
    person_id uuid NOT NULL REFERENCES people(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    PRIMARY KEY (provider, external_id),
    CHECK (provider <> 'TMDB' OR external_id ~ '^[1-9][0-9]*$')
);
CREATE INDEX person_external_ids_person_idx ON person_external_ids (person_id);

CREATE TABLE catalog_credits (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    work_id uuid NOT NULL REFERENCES authority_catalog_works(id) ON DELETE CASCADE,
    season_number integer,
    episode_number integer,
    person_id uuid NOT NULL REFERENCES people(id),
    provider varchar(24) NOT NULL CHECK (btrim(provider) <> ''),
    external_credit_id varchar(128) NOT NULL CHECK (btrim(external_credit_id) <> ''),
    credit_kind varchar(10) NOT NULL CHECK (credit_kind IN ('CAST', 'GUEST_CAST', 'CREW')),
    credited_name varchar(255) NOT NULL CHECK (btrim(credited_name) <> ''),
    department varchar(128) NOT NULL CHECK (btrim(department) <> ''),
    job varchar(128) NOT NULL CHECK (btrim(job) <> ''),
    character_name varchar(500),
    billing_order integer CHECK (billing_order >= 0),
    source_order integer NOT NULL CHECK (source_order >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE NULLS NOT DISTINCT (work_id, season_number, episode_number, provider, credit_kind, external_credit_id),
    CHECK (episode_number IS NULL OR season_number IS NOT NULL),
    CHECK (credit_kind <> 'GUEST_CAST' OR episode_number IS NOT NULL),
    FOREIGN KEY (work_id, season_number)
        REFERENCES authority_catalog_seasons(work_id, season_number) ON DELETE CASCADE,
    FOREIGN KEY (work_id, season_number, episode_number)
        REFERENCES authority_catalog_episodes(work_id, season_number, episode_number) ON DELETE CASCADE
);
CREATE INDEX catalog_credits_person_idx ON catalog_credits (person_id, work_id);

ALTER TABLE authority_catalog_imports ADD COLUMN credits_backfill_at timestamptz;
CREATE INDEX authority_catalog_credit_backfill_idx ON authority_catalog_imports (credits_backfill_at)
    WHERE status = 'SUCCESS' AND credits_backfill_at IS NOT NULL;

-- Choose one existing successful import per verified, published scope. A show's
-- generic import resolves to the first regular season, so prefer its explicit job.
WITH eligible AS (
    SELECT DISTINCT ON (projection.work_id, projection.season_number)
           job.media_type, job.tmdb_id, job.season_number
      FROM authority_catalog_projections projection
      JOIN authority_catalog_works work ON work.id = projection.work_id
      JOIN unified_videos entry ON entry.id = projection.unified_video_id
      LEFT JOIN authority_catalog_seasons season
        ON season.work_id = projection.work_id AND season.season_number = projection.season_number
      JOIN authority_catalog_imports job
        ON job.media_type = work.media_type AND job.tmdb_id = work.tmdb_id
       AND (job.season_number = projection.season_number OR (
           job.media_type = 'tv' AND job.season_number = -1 AND projection.season_number = (
               SELECT min(s.season_number) FROM authority_catalog_seasons s
                WHERE s.work_id = work.id AND s.season_number > 0
           )
       ))
     WHERE job.status = 'SUCCESS' AND entry.content_visibility = 'PUBLIC'
       AND NOT coalesce(entry.adult_restricted, false)
       AND (projection.media_type = 'movie' OR season.verified_at IS NOT NULL)
       AND NOT EXISTS (
           SELECT 1 FROM authority_catalog_imports rejected
            WHERE rejected.media_type = work.media_type AND rejected.tmdb_id = work.tmdb_id
              AND rejected.last_error = 'ADULT_SOURCE_RECORD'
       )
     ORDER BY projection.work_id, projection.season_number, job.season_number DESC
), scheduled AS (
    SELECT eligible.*, row_number() OVER (ORDER BY media_type, tmdb_id, season_number) - 1 AS offset_slot
      FROM eligible
)
UPDATE authority_catalog_imports job
   SET credits_backfill_at = now() + (scheduled.offset_slot * interval '5 seconds')
  FROM scheduled
 WHERE (job.media_type, job.tmdb_id, job.season_number) =
       (scheduled.media_type, scheduled.tmdb_id, scheduled.season_number);

-- rollback ALTER TABLE authority_catalog_imports DROP COLUMN credits_backfill_at;
-- rollback DROP TABLE catalog_credits, person_external_ids, people;
