package com.prodigalgal.ircs.opsalert.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.opsalert.domain.AlertEvent;
import com.prodigalgal.ircs.opsalert.domain.AlertSeverity;
import com.prodigalgal.ircs.opsalert.dto.AlertEventFilter;
import com.prodigalgal.ircs.opsalert.infrastructure.OpsAlertRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class OpsAlertQueryServiceTest {

    private final OpsAlertRepository repository = org.mockito.Mockito.mock(OpsAlertRepository.class);
    private final OpsAlertQueryService service = new OpsAlertQueryService(repository);

    @Test
    void cachesUnfilteredEventFirstPage() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        AlertEventFilter filter = new AlertEventFilter(null, null, null, null, null, null, null, null);
        Page<AlertEvent> events = new PageImpl<>(List.of(event("pod restart")), pageable, 1);
        when(repository.findEvents(pageable, filter)).thenReturn(events);

        service.findEvents(pageable, filter);
        service.findEvents(pageable, filter);

        verify(repository, times(1)).findEvents(pageable, filter);
    }

    @Test
    void filteredEventQueryBypassesFirstPageCache() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        AlertEventFilter filter = new AlertEventFilter(AlertSeverity.WARNING, null, null, null, null, null, null, null);
        Page<AlertEvent> events = new PageImpl<>(List.of(event("warning")), pageable, 1);
        when(repository.findEvents(pageable, filter)).thenReturn(events);

        assertEquals(1, service.findEvents(pageable, filter).getTotalElements());
        assertEquals(1, service.findEvents(pageable, filter).getTotalElements());

        verify(repository, times(2)).findEvents(pageable, filter);
    }

    private AlertEvent event(String summary) {
        Instant now = Instant.parse("2026-07-02T00:00:00Z");
        return new AlertEvent(
                UUID.randomUUID(),
                now,
                now,
                "ops",
                "service.health",
                AlertSeverity.INFO,
                "pod",
                "ircs-demo",
                "fingerprint",
                summary,
                "{}");
    }
}
