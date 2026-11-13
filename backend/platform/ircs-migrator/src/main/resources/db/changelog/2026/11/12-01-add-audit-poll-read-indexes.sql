-- liquibase formatted sql

-- 业务版本: 3.2.0。按当前运行时 SQL 补齐读索引；不改变数据、状态或唯一性契约。
-- 2026/06/add-performance-indexes.sql 未执行的旧轮询索引由此处现行排序索引替代；
-- enrichment 已走运行时工作队列，不再为不存在的 PENDING SQL 轮询增加写放大。

-- changeset codex:1785900000000-cover-download-poll runInTransaction:false
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cover_images_download_poll
    ON cover_images ((CASE status WHEN 'FAILED' THEN 0 ELSE 1 END),
                     next_retry_time ASC NULLS FIRST, updated_at ASC, id)
    WHERE storage_type = 'EXTERNAL'
      AND status IN ('UNPROCESSED', 'FAILED')
      AND nullif(trim(original_url), '') IS NOT NULL;
-- rollback DROP INDEX CONCURRENTLY IF EXISTS idx_cover_images_download_poll;

-- changeset codex:1785900000001-normalization-pending-poll runInTransaction:false
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_raw_videos_norm_retry_due
    ON raw_videos (updated_at ASC NULLS FIRST, id)
    WHERE normalization_status = 'PENDING';
-- rollback DROP INDEX CONCURRENTLY IF EXISTS idx_raw_videos_norm_retry_due;

-- changeset codex:1785900000002-aggregation-pending-poll runInTransaction:false
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_raw_videos_agg_runtime_poll
    ON raw_videos (aggregation_status_updated_at ASC NULLS FIRST, updated_at ASC NULLS FIRST, id)
    WHERE aggregation_status = 'PENDING';
-- rollback DROP INDEX CONCURRENTLY IF EXISTS idx_raw_videos_agg_runtime_poll;

-- changeset codex:1785900000003-members-email-lower runInTransaction:false
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_members_email_lower
    ON members (lower(email));
-- rollback DROP INDEX CONCURRENTLY IF EXISTS idx_members_email_lower;
