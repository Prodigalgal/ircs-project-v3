-- liquibase formatted sql

-- Business version: 3.5.1. Format-only repair of confirmed external-ID placeholders.
-- No title/name/source evidence, bindings, user data or pipeline states are changed.
-- Manual field locks are protected for both JSON arrays and JSON object keys.
-- Values such as 123/456 are never assumed to be placeholders.
-- SET clauses recheck the current locked row after any concurrent writer commits.
-- changeset codex:1785900000005-clear-authoritative-id-placeholders runInTransaction:true
SET LOCAL lock_timeout = '30s';
SET LOCAL statement_timeout = '120s';

WITH normalized AS (
    SELECT id, locked_fields,
           lower(btrim(douban_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) AS douban_id_value,
           lower(btrim(tmdb_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) AS tmdb_id_value,
           lower(btrim(imdb_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) AS imdb_id_value,
           lower(btrim(rotten_tomatoes_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) AS rotten_tomatoes_id_value
      FROM raw_videos
), candidates AS (
    SELECT id,
           (NOT coalesce(jsonb_exists(locked_fields, 'doubanId'), false)
            AND (douban_id_value IN ('', '-1', 'null', 'none', 'undefined', 'unknown', 'n/a', 'na', '无', '暂无', '未知', '未填写', '-', '0.0', '0.00', '-0', '内详', '不详', '佚名')
                 OR douban_id_value ~ '^0+$')) AS clear_douban_id,
           (NOT coalesce(jsonb_exists(locked_fields, 'tmdbId'), false)
            AND (tmdb_id_value IN ('', '-1', 'null', 'none', 'undefined', 'unknown', 'n/a', 'na', '无', '暂无', '未知', '未填写', '-', '0.0', '0.00', '-0', '内详', '不详', '佚名')
                 OR tmdb_id_value ~ '^0+$')) AS clear_tmdb_id,
           (NOT coalesce(jsonb_exists(locked_fields, 'imdbId'), false)
            AND (imdb_id_value IN ('', '-1', 'null', 'none', 'undefined', 'unknown', 'n/a', 'na', '无', '暂无', '未知', '未填写', '-', '0.0', '0.00', '-0', '内详', '不详', '佚名')
                 OR imdb_id_value ~ '^0+$' OR imdb_id_value ~ '^tt0+$')) AS clear_imdb_id,
           (NOT coalesce(jsonb_exists(locked_fields, 'rottenTomatoesId'), false)
            AND (rotten_tomatoes_id_value IN ('', '-1', 'null', 'none', 'undefined', 'unknown', 'n/a', 'na', '无', '暂无', '未知', '未填写', '-', '0.0', '0.00', '-0', '内详', '不详', '佚名')
                 OR rotten_tomatoes_id_value ~ '^0+$')) AS clear_rotten_tomatoes_id
      FROM normalized
)
UPDATE raw_videos v
SET douban_id = CASE WHEN c.clear_douban_id
        AND NOT coalesce(jsonb_exists(v.locked_fields, 'doubanId'), false)
        AND (lower(btrim(v.douban_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) IN ('', '-1', 'null', 'none', 'undefined', 'unknown', 'n/a', 'na', '无', '暂无', '未知', '未填写', '-', '0.0', '0.00', '-0', '内详', '不详', '佚名') OR lower(btrim(v.douban_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) ~ '^0+$')
        THEN NULL ELSE v.douban_id END,
    tmdb_id = CASE WHEN c.clear_tmdb_id
        AND NOT coalesce(jsonb_exists(v.locked_fields, 'tmdbId'), false)
        AND (lower(btrim(v.tmdb_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) IN ('', '-1', 'null', 'none', 'undefined', 'unknown', 'n/a', 'na', '无', '暂无', '未知', '未填写', '-', '0.0', '0.00', '-0', '内详', '不详', '佚名') OR lower(btrim(v.tmdb_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) ~ '^0+$')
        THEN NULL ELSE v.tmdb_id END,
    imdb_id = CASE WHEN c.clear_imdb_id
        AND NOT coalesce(jsonb_exists(v.locked_fields, 'imdbId'), false)
        AND (lower(btrim(v.imdb_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) IN ('', '-1', 'null', 'none', 'undefined', 'unknown', 'n/a', 'na', '无', '暂无', '未知', '未填写', '-', '0.0', '0.00', '-0', '内详', '不详', '佚名') OR lower(btrim(v.imdb_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) ~ '^0+$' OR lower(btrim(v.imdb_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) ~ '^tt0+$')
        THEN NULL ELSE v.imdb_id END,
    rotten_tomatoes_id = CASE WHEN c.clear_rotten_tomatoes_id
        AND NOT coalesce(jsonb_exists(v.locked_fields, 'rottenTomatoesId'), false)
        AND (lower(btrim(v.rotten_tomatoes_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) IN ('', '-1', 'null', 'none', 'undefined', 'unknown', 'n/a', 'na', '无', '暂无', '未知', '未填写', '-', '0.0', '0.00', '-0', '内详', '不详', '佚名') OR lower(btrim(v.rotten_tomatoes_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) ~ '^0+$')
        THEN NULL ELSE v.rotten_tomatoes_id END
FROM candidates c
WHERE v.id = c.id
  AND (c.clear_douban_id OR c.clear_tmdb_id OR c.clear_imdb_id OR c.clear_rotten_tomatoes_id);

WITH normalized AS (
    SELECT id, locked_fields,
           lower(btrim(douban_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) AS douban_id_value,
           lower(btrim(tmdb_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) AS tmdb_id_value,
           lower(btrim(imdb_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) AS imdb_id_value,
           lower(btrim(rotten_tomatoes_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) AS rotten_tomatoes_id_value
      FROM unified_videos
), candidates AS (
    SELECT id,
           (NOT coalesce(jsonb_exists(locked_fields, 'doubanId'), false)
            AND (douban_id_value IN ('', '-1', 'null', 'none', 'undefined', 'unknown', 'n/a', 'na', '无', '暂无', '未知', '未填写', '-', '0.0', '0.00', '-0', '内详', '不详', '佚名')
                 OR douban_id_value ~ '^0+$')) AS clear_douban_id,
           (NOT coalesce(jsonb_exists(locked_fields, 'tmdbId'), false)
            AND (tmdb_id_value IN ('', '-1', 'null', 'none', 'undefined', 'unknown', 'n/a', 'na', '无', '暂无', '未知', '未填写', '-', '0.0', '0.00', '-0', '内详', '不详', '佚名')
                 OR tmdb_id_value ~ '^0+$')) AS clear_tmdb_id,
           (NOT coalesce(jsonb_exists(locked_fields, 'imdbId'), false)
            AND (imdb_id_value IN ('', '-1', 'null', 'none', 'undefined', 'unknown', 'n/a', 'na', '无', '暂无', '未知', '未填写', '-', '0.0', '0.00', '-0', '内详', '不详', '佚名')
                 OR imdb_id_value ~ '^0+$' OR imdb_id_value ~ '^tt0+$')) AS clear_imdb_id,
           (NOT coalesce(jsonb_exists(locked_fields, 'rottenTomatoesId'), false)
            AND (rotten_tomatoes_id_value IN ('', '-1', 'null', 'none', 'undefined', 'unknown', 'n/a', 'na', '无', '暂无', '未知', '未填写', '-', '0.0', '0.00', '-0', '内详', '不详', '佚名')
                 OR rotten_tomatoes_id_value ~ '^0+$')) AS clear_rotten_tomatoes_id
      FROM normalized
)
UPDATE unified_videos v
SET douban_id = CASE WHEN c.clear_douban_id
        AND NOT coalesce(jsonb_exists(v.locked_fields, 'doubanId'), false)
        AND (lower(btrim(v.douban_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) IN ('', '-1', 'null', 'none', 'undefined', 'unknown', 'n/a', 'na', '无', '暂无', '未知', '未填写', '-', '0.0', '0.00', '-0', '内详', '不详', '佚名') OR lower(btrim(v.douban_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) ~ '^0+$')
        THEN NULL ELSE v.douban_id END,
    tmdb_id = CASE WHEN c.clear_tmdb_id
        AND NOT coalesce(jsonb_exists(v.locked_fields, 'tmdbId'), false)
        AND (lower(btrim(v.tmdb_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) IN ('', '-1', 'null', 'none', 'undefined', 'unknown', 'n/a', 'na', '无', '暂无', '未知', '未填写', '-', '0.0', '0.00', '-0', '内详', '不详', '佚名') OR lower(btrim(v.tmdb_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) ~ '^0+$')
        THEN NULL ELSE v.tmdb_id END,
    imdb_id = CASE WHEN c.clear_imdb_id
        AND NOT coalesce(jsonb_exists(v.locked_fields, 'imdbId'), false)
        AND (lower(btrim(v.imdb_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) IN ('', '-1', 'null', 'none', 'undefined', 'unknown', 'n/a', 'na', '无', '暂无', '未知', '未填写', '-', '0.0', '0.00', '-0', '内详', '不详', '佚名') OR lower(btrim(v.imdb_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) ~ '^0+$' OR lower(btrim(v.imdb_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) ~ '^tt0+$')
        THEN NULL ELSE v.imdb_id END,
    rotten_tomatoes_id = CASE WHEN c.clear_rotten_tomatoes_id
        AND NOT coalesce(jsonb_exists(v.locked_fields, 'rottenTomatoesId'), false)
        AND (lower(btrim(v.rotten_tomatoes_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) IN ('', '-1', 'null', 'none', 'undefined', 'unknown', 'n/a', 'na', '无', '暂无', '未知', '未填写', '-', '0.0', '0.00', '-0', '内详', '不详', '佚名') OR lower(btrim(v.rotten_tomatoes_id, chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(32) || chr(160) || chr(12288))) ~ '^0+$')
        THEN NULL ELSE v.rotten_tomatoes_id END
FROM candidates c
WHERE v.id = c.id
  AND (c.clear_douban_id OR c.clear_tmdb_id OR c.clear_imdb_id OR c.clear_rotten_tomatoes_id);

-- SET LOCAL restores the prior timeout settings on transaction commit or rollback.
-- The repair is deliberately forward-only: original source evidence remains in raw_metadata.
-- rollback SELECT 1;
