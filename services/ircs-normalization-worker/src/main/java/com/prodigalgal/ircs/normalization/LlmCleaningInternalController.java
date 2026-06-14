package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.common.security.InternalServiceAuthHeaders;
import com.prodigalgal.ircs.contracts.normalization.LlmCleaningRunResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/normalization/llm-cleaning")
class LlmCleaningInternalController {

    private final LlmCleaningService cleaningService;
    private final NormalizationInternalAccessPolicy accessPolicy;

    @PostMapping("/{kind}/run")
    ResponseEntity<LlmCleaningRunResponse> run(
            @PathVariable String kind,
            @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_ID, required = false) String serviceId,
            @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_TOKEN, required = false) String serviceToken,
                @RequestHeader(value = InternalServiceAuthHeaders.SERVICE_SCOPES, required = false) String serviceScopes) {
        accessPolicy.assertAccess(serviceId, serviceToken, serviceScopes);
        LlmCleaningKind cleaningKind = parse(kind);
        LlmCleaningService.LlmCleaningResult result = cleaningService.cleanBatch(cleaningKind);
        return ResponseEntity.accepted().body(new LlmCleaningRunResponse(
                result.kind().name(),
                result.status(),
                result.candidates(),
                result.mapped(),
                result.noise(),
                result.reason()));
    }

    private LlmCleaningKind parse(String value) {
        try {
            return LlmCleaningKind.parse(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
