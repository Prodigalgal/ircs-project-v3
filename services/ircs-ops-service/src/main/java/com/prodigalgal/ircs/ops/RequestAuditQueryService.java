package com.prodigalgal.ircs.ops;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RequestAuditQueryService {

    private final RequestAuditRepository repository;

    public Page<RequestAuditLogResponse> findAll(
            Pageable pageable,
            String username,
            String requestSource,
            String method,
            String path,
            Integer statusCode,
            String statusClass,
            String clientIp,
            Instant from,
            Instant to) {
        return repository.findAll(pageable, username, requestSource, method, path, statusCode, statusClass, clientIp, from, to);
    }

    public RequestAuditSummaryResponse summarize() {
        return repository.summarize(Instant.now().minus(24, ChronoUnit.HOURS));
    }
}
