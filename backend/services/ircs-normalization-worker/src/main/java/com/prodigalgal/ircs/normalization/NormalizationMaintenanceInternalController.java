package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.common.security.InternalServiceAuthHeaders;
import com.prodigalgal.ircs.contracts.normalization.NormalizationMaintenanceRunResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/normalization/maintenance")
class NormalizationMaintenanceInternalController {

    private final RawVideoNormalizationService normalizationService;
    private final NormalizationInternalAccessPolicy accessPolicy;

    @PostMapping("/preview")
    List<NormalizationPreviewResult> preview(
            @RequestBody PreviewRequest request,
            @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_ID, required = false) String serviceId,
            @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_TOKEN, required = false) String serviceToken,
            @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_SCOPES, required = false) String serviceScopes) {
        accessPolicy.assertAccess(serviceId, serviceToken, serviceScopes);
        return normalizationService.preview(request.rawVideoIds());
    }

    record PreviewRequest(List<UUID> rawVideoIds) {}

    @PostMapping("/raw-videos/reset-normalization")
    ResponseEntity<NormalizationMaintenanceRunResponse> resetNormalization(
            @RequestParam(defaultValue = "5") int sampleLimit,
            @RequestParam(defaultValue = "false") boolean enqueue,
            @RequestParam(defaultValue = "500") int batchSize,
            @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_ID, required = false) String serviceId,
            @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_TOKEN, required = false) String serviceToken,
            @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_SCOPES, required = false) String serviceScopes) {
        accessPolicy.assertAccess(serviceId, serviceToken, serviceScopes);
        return ResponseEntity.accepted()
                .body(normalizationService.resetAllNormalizationPending(sampleLimit, enqueue, batchSize));
    }
}
