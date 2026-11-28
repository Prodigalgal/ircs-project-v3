--liquibase formatted sql

--changeset prodigalgal:2026-11-add-magnet-provider-therarbg
-- Adds the one source from this batch that exposes a JSON search API reachable without a browser
-- session or an account. thepiratebay.org is already served by the existing `thepiratebay` provider
-- (apibay.org) and yts.pw by `yts_bz` (movies-api.accel.li is that site's own API), so neither is
-- re-seeded here.
INSERT INTO magnet_providers (
    id,
    code,
    name,
    provider_type,
    base_url,
    enabled,
    priority,
    risk_level,
    supported_external_ids,
    min_delay_ms,
    max_delay_ms,
    timeout_ms,
    result_limit,
    auto_approve_allowed,
    content_policy
) VALUES
(
    gen_random_uuid(),
    'therarbg',
    'TheRarBg API',
    'THE_RARBG',
    'https://therarbg.com',
    true,
    35,
    'HIGH',
    '["IMDB","TITLE_YEAR","TITLE"]'::jsonb,
    3000,
    8000,
    15000,
    30,
    false,
    '使用 therarbg.com 公开 JSON 搜索接口（/api/v1/search/）。IMDb 查询走精确 imdb 参数，标题查询走 q 参数；返回 infoHash 与现成 magnet。路径必须带尾斜杠：/api/v1/search 会 301，而出站客户端不跟随重定向。'
)
ON CONFLICT (code) DO NOTHING;

--changeset prodigalgal:2026-11-disable-retired-magnet-providers
-- The scheduler enumerates enabled rows before applying the allowlist at request time.
-- Disable the retired rows so missing candidates do not turn into permanent provider failures.
UPDATE magnet_providers
SET enabled = false,
    updated_at = NOW(),
    version = COALESCE(version, 0) + 1
WHERE code IN ('thepiratebay_org', 'ext_to')
  AND enabled;

--changeset prodigalgal:2026-11-merge-magnet-real-provider-allowlist
-- Retired from the allowlist:
--   EXT_TO    - ext.to and extto.com answer Cloudflare's managed challenge on every path, including a
--               headed browser, so the provider can never succeed. Re-enable it only alongside a
--               working Cloudflare clearance source.
--   TORRENTCLAW - the site withholds every infoHash and magnetUrl from an anonymous caller and asks
--               for an account. Magnets are the only thing this service consumes, so the provider is
--               not registered at all.
INSERT INTO system_configs (id, created_at, updated_at, version, config_key, config_value, description)
VALUES (
    gen_random_uuid(),
    NOW(),
    NOW(),
    0,
    'app.magnet.real-provider.allowlist',
    'YTS_BZ,THE_PIRATE_BAY,EZTV,THE_RARBG',
    '允许调用的真实磁链 Provider 类型或编码；EXT_TO 因 Cloudflare 托管挑战不可解、TORRENTCLAW 因磁力字段需账号而移除'
)
ON CONFLICT (config_key) DO UPDATE
SET config_value = (
        SELECT string_agg(entry, ',' ORDER BY position)
        FROM (
            SELECT btrim(parsed.value) AS entry, parsed.ordinality AS position
            FROM regexp_split_to_table(COALESCE(system_configs.config_value, ''), ',')
                 WITH ORDINALITY AS parsed(value, ordinality)
            WHERE btrim(parsed.value) <> ''
              AND upper(btrim(parsed.value)) NOT IN ('EXT_TO', 'THE_PIRATE_BAY_FRONTEND')
            UNION ALL
            SELECT 'THE_RARBG', 9223372036854775807::bigint
            WHERE NOT EXISTS (
                SELECT 1
                FROM regexp_split_to_table(COALESCE(system_configs.config_value, ''), ',') AS existing(value)
                WHERE upper(btrim(existing.value)) = 'THE_RARBG'
            )
        ) AS merged
    ),
    updated_at = NOW(),
    version = COALESCE(system_configs.version, 0) + 1;

--changeset prodigalgal:2026-11-merge-magnet-traffic-gaps
INSERT INTO system_configs (id, created_at, updated_at, version, config_key, config_value, description)
VALUES (
    gen_random_uuid(),
    NOW(),
    NOW(),
    0,
    'app.magnet.traffic.provider-gap-ms',
    'YTS_BZ=3000,THE_PIRATE_BAY=8000,EZTV=3000,THE_RARBG=4000',
    '磁链 Provider 单站请求间隔覆盖(毫秒)，格式 PROVIDER=MS'
)
ON CONFLICT (config_key) DO UPDATE
SET config_value = (
        SELECT string_agg(entry, ',' ORDER BY position)
        FROM (
            SELECT btrim(parsed.value) AS entry, parsed.ordinality AS position
            FROM regexp_split_to_table(COALESCE(system_configs.config_value, ''), ',')
                 WITH ORDINALITY AS parsed(value, ordinality)
            WHERE btrim(parsed.value) <> ''
              AND upper(btrim(split_part(parsed.value, '=', 1)))
                    NOT IN ('EXT_TO', 'THE_PIRATE_BAY_FRONTEND')
            UNION ALL
            SELECT 'THE_RARBG=4000', 9223372036854775807::bigint
            WHERE NOT EXISTS (
                SELECT 1
                FROM regexp_split_to_table(COALESCE(system_configs.config_value, ''), ',') AS existing(value)
                WHERE upper(btrim(split_part(existing.value, '=', 1))) = 'THE_RARBG'
            )
        ) AS merged
    ),
    updated_at = NOW(),
    version = COALESCE(system_configs.version, 0) + 1;
