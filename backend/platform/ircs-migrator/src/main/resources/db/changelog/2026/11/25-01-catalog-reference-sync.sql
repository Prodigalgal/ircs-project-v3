-- liquibase formatted sql

-- changeset codex:1795556400001-catalog-reference-sync
ALTER TABLE catalog_authority_import_jobs
    ADD COLUMN refresh_generation bigint NOT NULL DEFAULT 0;

CREATE TABLE catalog_reference_feed_states (
    provider varchar(32) NOT NULL,
    feed varchar(32) NOT NULL,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    lease_token uuid,
    lease_until timestamptz,
    input_fingerprint varchar(64),
    etag varchar(512),
    last_modified varchar(128),
    last_success_at timestamptz,
    last_error varchar(64),
    PRIMARY KEY (provider, feed),
    CHECK ((lease_token IS NULL) = (lease_until IS NULL))
);

CREATE TABLE catalog_reference_work_refreshes (
    provider varchar(32) NOT NULL,
    work_id uuid NOT NULL REFERENCES catalog_works(id) ON DELETE CASCADE,
    next_attempt_at timestamptz NOT NULL,
    last_checked_at timestamptz NOT NULL DEFAULT now(),
    last_result varchar(32) NOT NULL,
    PRIMARY KEY (provider, work_id)
);

-- rollback DROP TABLE catalog_reference_work_refreshes, catalog_reference_feed_states;
-- rollback ALTER TABLE catalog_authority_import_jobs DROP COLUMN refresh_generation;
