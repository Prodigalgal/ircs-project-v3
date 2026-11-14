-- liquibase formatted sql

-- 业务版本: 3.4.11。为 aggregation 的完整标题预筛提供 trigram GIN 索引。
-- precondition 防止并发索引创建失败留下的同名 invalid index 被 IF NOT EXISTS 静默跳过。
-- changeset codex:1785900000004-aggregation-full-title-trgm runInTransaction:false
-- preconditions onFail:HALT onError:HALT
-- precondition-sql-check expectedResult:0 SELECT count(*) FROM pg_class c LEFT JOIN pg_index i ON i.indexrelid = c.oid WHERE c.relname = 'idx_unified_videos_full_title_trgm' AND c.relnamespace = (SELECT relnamespace FROM pg_class WHERE oid = 'unified_videos'::regclass) AND (i.indexrelid IS NULL OR NOT i.indisvalid OR i.indrelid <> 'unified_videos'::regclass)
SET lock_timeout = '30s';
SET statement_timeout = '120s';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_unified_videos_full_title_trgm
    ON unified_videos USING GIN ((coalesce(title, '') || ' ' || coalesce(subtitle, '')) gin_trgm_ops);
RESET statement_timeout;
RESET lock_timeout;
-- rollback DROP INDEX CONCURRENTLY IF EXISTS idx_unified_videos_full_title_trgm;
