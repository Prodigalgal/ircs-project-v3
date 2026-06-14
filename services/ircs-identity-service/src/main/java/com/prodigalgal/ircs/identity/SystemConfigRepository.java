package com.prodigalgal.ircs.identity;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SystemConfigRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentMap<String, Optional<String>> cache = new ConcurrentHashMap<>();

    public Optional<String> findValue(String key) {
        return cache.computeIfAbsent(key, this::findValueFromDatabase);
    }

    public void evict(String key) {
        cache.remove(key);
    }

    public long upsertValue(String key, String value) {
        Long revision = jdbcTemplate.queryForObject(
                """
                insert into system_configs (id, created_at, updated_at, version, config_key, config_value)
                values (gen_random_uuid(), now(), now(), 1, ?, ?)
                on conflict (config_key) do update
                   set config_value = excluded.config_value,
                       updated_at = now(),
                       version = coalesce(system_configs.version, 0) + 1
                returning coalesce(version, 0)
                """,
                Long.class,
                key,
                value);
        evict(key);
        return revision == null ? 0L : revision;
    }

    private Optional<String> findValueFromDatabase(String key) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "select config_value from system_configs where config_key = ?",
                    String.class,
                    key));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }
}
