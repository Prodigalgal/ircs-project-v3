package com.prodigalgal.ircs.magnet;

import com.prodigalgal.ircs.common.web.PageEnvelope;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class MagnetController {

    private static final int MAX_LINK_PAGE_SIZE = 200;

    private final MagnetQueryService magnetQueryService;

    @GetMapping("/magnet-providers")
    public ResponseEntity<List<MagnetProviderSummary>> listProviders() {
        return ResponseEntity.ok(magnetQueryService.listProviders());
    }

    @GetMapping("/magnet-providers/{id}")
    public ResponseEntity<MagnetProviderSummary> getProvider(@PathVariable UUID id) {
        return magnetQueryService.findProvider(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/magnet-providers")
    public ResponseEntity<MagnetProviderSummary> createProvider(@RequestBody MagnetProviderRequest request) {
        MagnetProviderSummary result = magnetQueryService.createProvider(request);
        return ResponseEntity.created(ServletUriComponentsBuilder
                        .fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(result.id())
                        .toUri())
                .body(result);
    }

    @PutMapping("/magnet-providers/{id}")
    public ResponseEntity<MagnetProviderSummary> updateProvider(
            @PathVariable UUID id,
            @RequestBody MagnetProviderRequest request) {
        return ResponseEntity.ok(magnetQueryService.updateProvider(id, request));
    }

    @GetMapping("/magnets/unified/{id}")
    public ResponseEntity<List<MagnetLinkSummary>> getUnifiedMagnets(@PathVariable UUID id) {
        return ResponseEntity.ok(magnetQueryService.findApprovedLinks(id));
    }

    @GetMapping("/magnets/unified/{id}/links")
    public ResponseEntity<List<MagnetLinkSummary>> getUnifiedMagnetLinks(@PathVariable UUID id) {
        return ResponseEntity.ok(magnetQueryService.findLinks(id));
    }

    /**
     * Enumerates links by status so the review queue is reachable. Defaults to {@code CANDIDATE},
     * which is where every provider that does not auto-approve writes its results.
     */
    @GetMapping("/magnets/links")
    public ResponseEntity<PageEnvelope<MagnetLinkSummary>> getMagnetLinksByStatus(
            @RequestParam(defaultValue = "CANDIDATE") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        String normalizedStatus = normalizeLinkStatus(status);
        if (page < 0) {
            throw badRequest("Page must be greater than or equal to zero");
        }
        if (size < 1 || size > MAX_LINK_PAGE_SIZE) {
            throw badRequest("Page size must be between 1 and " + MAX_LINK_PAGE_SIZE);
        }
        int rowOffset;
        try {
            rowOffset = Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw badRequest("Page and size exceed the supported result range");
        }
        List<MagnetLinkSummary> content = magnetQueryService.findLinksByStatus(normalizedStatus, size, rowOffset);
        return ResponseEntity.ok(PageEnvelope.of(
                content, page, size, magnetQueryService.countLinksByStatus(normalizedStatus)));
    }

    private String normalizeLinkStatus(String status) {
        if (status == null || status.isBlank()) {
            throw badRequest("Magnet link status is required");
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "APPROVED", "CANDIDATE", "REJECTED", "HIDDEN" -> normalized;
            default -> throw badRequest("Unsupported magnet link status");
        };
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    @GetMapping("/magnets/unified/{id}/jobs")
    public ResponseEntity<List<MagnetSearchJobSummary>> getUnifiedMagnetJobs(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(magnetQueryService.findSearchJobs(id, limit));
    }

    @GetMapping("/magnets/search-jobs/{id}/runs")
    public ResponseEntity<List<MagnetProviderRunSummary>> getMagnetProviderRuns(@PathVariable UUID id) {
        return ResponseEntity.ok(magnetQueryService.findProviderRuns(id));
    }

    @PostMapping("/magnets/search/unified/{id}")
    public ResponseEntity<MagnetSearchJobSummary> searchUnifiedMagnets(@PathVariable UUID id) {
        return ResponseEntity.ok(magnetQueryService.enqueueUnifiedSearch(id));
    }

    @PutMapping("/magnets/unified/{id}/links/{linkId}/status")
    public ResponseEntity<MagnetLinkSummary> updateUnifiedMagnetLinkStatus(
            @PathVariable UUID id,
            @PathVariable UUID linkId,
            @RequestBody MagnetLinkStatusRequest request) {
        return ResponseEntity.ok(magnetQueryService.updateLinkStatus(id, linkId, request));
    }

    @DeleteMapping("/magnets/unified/{id}/links/{linkId}")
    public ResponseEntity<Void> deleteUnifiedMagnetLink(
            @PathVariable UUID id,
            @PathVariable UUID linkId) {
        magnetQueryService.deleteLink(id, linkId);
        return ResponseEntity.noContent().build();
    }
}
