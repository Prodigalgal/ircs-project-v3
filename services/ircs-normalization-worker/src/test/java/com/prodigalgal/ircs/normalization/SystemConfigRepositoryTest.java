package com.prodigalgal.ircs.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SystemConfigRepositoryTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final SystemConfigRepository repository =
            new SystemConfigRepository(jdbcTemplate, SystemConfigRepository.DEFAULT_CACHE_TTL);

    @Test
    void cachesValuesUntilEvicted() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("normalization.max-retries")))
                .thenReturn("5")
                .thenReturn("7");

        assertEquals("5", repository.findValue("normalization.max-retries").orElseThrow());
        assertEquals("5", repository.findValue("normalization.max-retries").orElseThrow());

        repository.evict("normalization.max-retries");

        assertEquals("7", repository.findValue("normalization.max-retries").orElseThrow());
        verify(jdbcTemplate, times(2))
                .queryForObject(anyString(), eq(String.class), eq("normalization.max-retries"));
    }

    @Test
    void refreshesAfterTtlWhenConfigChangeEventWasMissed() {
        AtomicLong ticker = new AtomicLong(0L);
        SystemConfigRepository ttlRepository =
                SystemConfigRepository.forTest(jdbcTemplate, Duration.ofSeconds(30), ticker::get);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("normalization.max-retries")))
                .thenReturn("5")
                .thenReturn("9");

        assertEquals("5", ttlRepository.findValue("normalization.max-retries").orElseThrow());
        ticker.set(Duration.ofSeconds(29).toNanos());
        assertEquals("5", ttlRepository.findValue("normalization.max-retries").orElseThrow());

        ticker.set(Duration.ofSeconds(30).toNanos());

        assertEquals("9", ttlRepository.findValue("normalization.max-retries").orElseThrow());
        verify(jdbcTemplate, times(2))
                .queryForObject(anyString(), eq(String.class), eq("normalization.max-retries"));
    }
}
