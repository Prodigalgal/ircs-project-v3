--liquibase formatted sql

--changeset codex:1795470000010-collection-plans-and-runs
CREATE TABLE collection_runs (
    id uuid PRIMARY KEY,
    collection_plan_id uuid NOT NULL REFERENCES collection_plans (id),
    collection_source_id uuid NOT NULL REFERENCES collection_sources (id),
    resumed_from_run_id uuid,
    exclusive_source boolean NOT NULL,
    execution_parameters jsonb NOT NULL,
    status varchar(16) NOT NULL
        CHECK (status IN ('QUEUED', 'RUNNING', 'PAUSED', 'STOPPED', 'COMPLETED', 'FAILED')),
    start_page integer NOT NULL CHECK (start_page > 0),
    checkpoint_page integer NOT NULL CHECK (checkpoint_page >= start_page),
    queued_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    finished_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now(),
    total_found bigint NOT NULL DEFAULT 0 CHECK (total_found >= 0),
    processed_count bigint NOT NULL DEFAULT 0 CHECK (processed_count >= 0),
    success_count bigint NOT NULL DEFAULT 0 CHECK (success_count >= 0),
    failure_count bigint NOT NULL DEFAULT 0 CHECK (failure_count >= 0),
    inserted_count bigint NOT NULL DEFAULT 0 CHECK (inserted_count >= 0),
    updated_count bigint NOT NULL DEFAULT 0 CHECK (updated_count >= 0),
    ignored_count bigint NOT NULL DEFAULT 0 CHECK (ignored_count >= 0),
    last_error_message text,
    UNIQUE (collection_plan_id, id),
    FOREIGN KEY (collection_plan_id, resumed_from_run_id)
        REFERENCES collection_runs (collection_plan_id, id),
    CHECK (jsonb_typeof(execution_parameters) = 'object'),
    CHECK (NOT jsonb_exists_any(execution_parameters, ARRAY['headers', 'proxy_username', 'proxy_password'])),
    CHECK ((status IN ('PAUSED', 'STOPPED', 'COMPLETED', 'FAILED')) = (finished_at IS NOT NULL))
);

CREATE UNIQUE INDEX collection_runs_one_active_per_plan
    ON collection_runs (collection_plan_id) WHERE status IN ('QUEUED', 'RUNNING');
CREATE UNIQUE INDEX collection_runs_one_exclusive_per_source
    ON collection_runs (collection_source_id)
    WHERE exclusive_source AND status IN ('QUEUED', 'RUNNING');
CREATE INDEX collection_runs_active_source
    ON collection_runs (collection_source_id, collection_plan_id)
    WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX collection_runs_active_heartbeat
    ON collection_runs (updated_at, id) WHERE status IN ('QUEUED', 'RUNNING');

ALTER TABLE collection_plans
    ADD COLUMN current_run_id uuid,
    ADD COLUMN archived_at timestamptz,
    ADD CONSTRAINT collection_plans_current_run_fk
        FOREIGN KEY (id, current_run_id) REFERENCES collection_runs (collection_plan_id, id);

-- Keep historical execution evidence when a plan is removed from the operator console.
ALTER TABLE collection_plans
    DROP CONSTRAINT IF EXISTS uc_collection_tasks_name,
    DROP CONSTRAINT IF EXISTS uc_collection_plans_name;
CREATE UNIQUE INDEX collection_plans_active_name ON collection_plans (name) WHERE archived_at IS NULL;

ALTER TABLE collection_plans
    DROP COLUMN status,
    DROP COLUMN version,
    DROP COLUMN current_page,
    DROP COLUMN last_execution_time,
    DROP COLUMN stat_start_time,
    DROP COLUMN stat_end_time,
    DROP COLUMN stat_total_found,
    DROP COLUMN stat_processed,
    DROP COLUMN stat_success,
    DROP COLUMN stat_failed,
    DROP COLUMN stat_inserted,
    DROP COLUMN stat_updated,
    DROP COLUMN stat_ignored,
    DROP COLUMN last_error_message;

CREATE VIEW collection_plan_details AS
SELECT p.id,
       p.name,
       p.enabled,
       p.collection_source_id,
       p.current_run_id,
       p.cron_expression,
       p.time_zone,
       p.task_type,
       p.start_page,
       p.end_page,
       p.filter_type,
       p.filter_hours,
       p.filter_keywords,
       p.request_delay_type,
       p.fixed_delay_ms,
       p.random_delay_min_ms,
       p.random_delay_max_ms,
       p.timeout_ms,
       p.max_retries,
       p.user_agent,
       p.enable_random_ua,
       p.use_custom_proxy,
       p.proxy_type,
       p.proxy_host,
       p.proxy_port,
       p.proxy_username,
       p.headers,
       p.created_at,
       GREATEST(p.updated_at, r.updated_at AT TIME ZONE 'UTC') AS updated_at,
       coalesce(r.status, 'IDLE') AS status,
       r.checkpoint_page AS current_page,
       r.updated_at AS last_execution_time,
       r.started_at AS stat_start_time,
       r.finished_at AS stat_end_time,
       coalesce(r.total_found, 0) AS stat_total_found,
       coalesce(r.processed_count, 0) AS stat_processed,
       coalesce(r.success_count, 0) AS stat_success,
       coalesce(r.failure_count, 0) AS stat_failed,
       coalesce(r.inserted_count, 0) AS stat_inserted,
       coalesce(r.updated_count, 0) AS stat_updated,
       coalesce(r.ignored_count, 0) AS stat_ignored,
       r.last_error_message
  FROM collection_plans p
  LEFT JOIN collection_runs r ON r.id = p.current_run_id
 WHERE p.archived_at IS NULL;
