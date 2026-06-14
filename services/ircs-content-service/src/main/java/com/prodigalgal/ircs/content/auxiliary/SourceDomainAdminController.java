package com.prodigalgal.ircs.content.auxiliary;

import com.prodigalgal.ircs.content.auxiliary.AuxiliaryAdminDtos.SourceDomainRequest;
import com.prodigalgal.ircs.content.auxiliary.AuxiliaryAdminDtos.SourceDomainResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/source-domains")
public class SourceDomainAdminController {

    private final AuxiliaryAdminService service;

    @GetMapping
    public ResponseEntity<Page<SourceDomainResponse>> getAll(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID dataSourceId) {
        return ResponseEntity.ok(service.findSourceDomains(pageable, keyword, dataSourceId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SourceDomainResponse> getOne(@PathVariable UUID id) {
        return service.findSourceDomain(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<SourceDomainResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody SourceDomainRequest request) {
        return ResponseEntity.ok(service.updateSourceDomain(id, request));
    }
}
