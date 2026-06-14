package com.prodigalgal.ircs.storage.image;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class SystemConfigRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentMap<String, Optional<String>> cache = new ConcurrentHashMap<>();

    Optional<String> findValue(String key) {
        return cache.computeIfAbsent(key, this::findValueFromDatabase);
    }

    void evict(String key) {
        cache.remove(key);
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
