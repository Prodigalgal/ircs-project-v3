package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.common.security.InternalServiceAuthHeaders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/aggregation/unified-videos/{source}/corrections")
@RequiredArgsConstructor
class InternalAggregationBindingCorrectionController {
    private final AggregationBindingCorrectionService correctionService;
    private final AggregationInternalAccessPolicy accessPolicy;

    @PostMapping("/preview")
    AggregationBindingCorrectionService.CorrectionPlan preview(@PathVariable UUID source,
            @Valid @RequestBody CorrectionRequest request,
            @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_ID, required = false) String serviceId,
            @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_TOKEN, required = false) String serviceToken,
            @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_SCOPES, required = false) String scopes) {
        accessPolicy.assertAccess(serviceId, serviceToken, scopes);
        return correctionService.preview(source, request.targetCatalogEntryId(), request.sourceRecordIds());
    }

    @PostMapping("/apply")
    AggregationBindingCorrectionService.CorrectionPlan apply(@PathVariable UUID source,
            @Valid @RequestBody CorrectionRequest request,
            @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_ID, required = false) String serviceId,
            @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_TOKEN, required = false) String serviceToken,
            @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_SCOPES, required = false) String scopes) {
        accessPolicy.assertCorrectionAccess(serviceId, serviceToken, scopes);
        return correctionService.apply(source, request.targetCatalogEntryId(), request.sourceRecordIds(),
                request.fingerprint(), serviceId);
    }

    record CorrectionRequest(
            @NotNull @Size(min = 1, max = AggregationBindingCorrectionService.MAX_SOURCE_RECORDS) List<@NotNull UUID> sourceRecordIds,
            UUID targetCatalogEntryId,
            @Pattern(regexp = "[0-9a-f]{64}") String fingerprint) { }
}
