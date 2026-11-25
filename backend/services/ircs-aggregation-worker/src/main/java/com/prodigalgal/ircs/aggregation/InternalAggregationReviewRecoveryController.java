package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.common.security.InternalServiceAuthHeaders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/aggregation/raw-videos/recover-reviewed")
@RequiredArgsConstructor
class InternalAggregationReviewRecoveryController {
    private final AggregationReviewRecoveryService recovery;
    private final AggregationInternalAccessPolicy accessPolicy;

    @PostMapping
    AggregationReviewRecoveryService.RecoveryResponse recover(@Valid @RequestBody RecoveryRequest request,
            @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_ID, required = false) String serviceId,
            @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_TOKEN, required = false) String serviceToken,
            @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_SCOPES, required = false) String scopes) {
        accessPolicy.assertCorrectionAccess(serviceId, serviceToken, scopes);
        return recovery.recover(request.rawVideoIds(), Boolean.TRUE.equals(request.execute()));
    }

    record RecoveryRequest(
            @NotNull @Size(min = 1, max = AggregationReviewRecoveryService.MAX_RAW_VIDEOS) List<@NotNull UUID> rawVideoIds,
            Boolean execute) { }
}
