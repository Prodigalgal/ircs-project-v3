-- liquibase formatted sql

-- changeset codex:1795380000002-tmdb-authority-inventory
CREATE TABLE authority_tmdb_inventory_state (
    media_type varchar(8) PRIMARY KEY CHECK (media_type IN ('movie', 'tv')),
    active_slot smallint NOT NULL DEFAULT 0 CHECK (active_slot IN (0, 1)),
    snapshot_date date,
    row_count integer NOT NULL DEFAULT 0 CHECK (row_count >= 0),
    last_success_at timestamptz,
    attempt_date date,
    last_attempt_at timestamptz,
    lease_token uuid,
    lease_until timestamptz,
    retry_at timestamptz NOT NULL DEFAULT '-infinity',
    last_error_code varchar(64),
    CHECK ((lease_token IS NULL) = (lease_until IS NULL)),
    CHECK ((snapshot_date IS NULL) = (row_count = 0))
);

INSERT INTO authority_tmdb_inventory_state (media_type) VALUES ('movie'), ('tv');

CREATE TABLE authority_tmdb_inventory_entries (
    media_type varchar(8) NOT NULL REFERENCES authority_tmdb_inventory_state(media_type),
    slot smallint NOT NULL CHECK (slot IN (0, 1)),
    tmdb_id bigint NOT NULL CHECK (tmdb_id > 0),
    title text NOT NULL,
    popularity double precision NOT NULL CHECK (popularity >= 0 AND popularity < 'Infinity'::double precision),
    video boolean NOT NULL DEFAULT false,
    PRIMARY KEY (media_type, slot, tmdb_id)
);

CREATE INDEX authority_tmdb_inventory_popularity_idx
    ON authority_tmdb_inventory_entries (media_type, slot, popularity DESC, tmdb_id);

CREATE VIEW authority_tmdb_inventory AS
SELECT e.media_type, e.tmdb_id, e.title, e.popularity, s.snapshot_date, e.video
  FROM authority_tmdb_inventory_state s
  JOIN authority_tmdb_inventory_entries e ON e.media_type = s.media_type AND e.slot = s.active_slot
 WHERE s.snapshot_date IS NOT NULL;

-- rollback DROP VIEW authority_tmdb_inventory;
-- rollback DROP TABLE authority_tmdb_inventory_entries;
-- rollback DROP TABLE authority_tmdb_inventory_state;
