--liquibase formatted sql

--changeset prodigalgal:2026-11-magnet-tracker-list
-- Public trackers come and go, so the announce list appended to magnet links must be changeable without
-- a redeploy. The runner falls back to its built-in list when this value is blank; setting it to a
-- value that parses to nothing (for example a single placeholder) means "append nothing".
INSERT INTO system_configs (id, created_at, updated_at, version, config_key, config_value, description)
VALUES (
    gen_random_uuid(),
    NOW(),
    NOW(),
    0,
    'app.magnet.tracker-list',
    '',
    '附加到磁力链接的公共 tracker 列表，逗号或换行分隔，如 udp://tracker.example:1337/announce；留空使用内置列表'
)
ON CONFLICT (config_key) DO UPDATE
SET description = EXCLUDED.description,
    updated_at = NOW(),
    version = COALESCE(system_configs.version, 0) + 1;

--changeset prodigalgal:2026-11-magnet-tracker-append-enabled
-- Keep automatic tracker appending enabled for existing deployments by default; preserve an
-- operator's explicit opt-out when the migration runs.
INSERT INTO system_configs (id, created_at, updated_at, version, config_key, config_value, description)
VALUES (
    gen_random_uuid(),
    NOW(),
    NOW(),
    0,
    'app.magnet.tracker-append-enabled',
    'true',
    '是否将配置的公共 tracker 追加到 Provider 返回的磁力链接；默认启用'
)
ON CONFLICT (config_key) DO NOTHING;
