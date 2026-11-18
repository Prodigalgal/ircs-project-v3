-- liquibase formatted sql

-- 业务版本 3.8.1。保留现有请求状态并记录资源复查结果。

-- changeset codex:20261119-catalog-discovery-resource-confirmation
ALTER TABLE catalog_discovery_requests ADD COLUMN fulfilled_at timestamptz;
-- rollback SELECT 1;

-- changeset codex:20261119-catalog-discovery-task-request-index runInTransaction:false
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collection_tasks_catalog_discovery_request
    ON collection_tasks ((headers -> 'ircsCatalogDiscovery' ->> 'requestKey'), data_source_id);
-- rollback DROP INDEX CONCURRENTLY IF EXISTS idx_collection_tasks_catalog_discovery_request;
