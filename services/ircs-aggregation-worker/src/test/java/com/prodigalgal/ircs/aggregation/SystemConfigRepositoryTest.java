package com.prodigalgal.ircs.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SystemConfigRepositoryTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final SystemConfigRepository repository = new SystemConfigRepository(jdbcTemplate);

    @Test
    void cachesValuesUntilEvicted() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("aggregation.batch-size")))
                .thenReturn("5")
                .thenReturn("7");

        assertEquals("5", repository.findValue("aggregation.batch-size").orElseThrow());
        assertEquals("5", repository.findValue("aggregation.batch-size").orElseThrow());

        repository.evict("aggregation.batch-size");

        assertEquals("7", repository.findValue("aggregation.batch-size").orElseThrow());
        verify(jdbcTemplate, times(2))
                .queryForObject(anyString(), eq(String.class), eq("aggregation.batch-size"));
    }
}
