package com.prodigalgal.ircs.content.people.controller;

import com.prodigalgal.ircs.common.web.PageEnvelope;
import com.prodigalgal.ircs.content.people.application.PeopleAdminService;
import com.prodigalgal.ircs.content.people.domain.PeopleResource;
import com.prodigalgal.ircs.content.people.dto.PeopleAdminDtos.PersonPatchRequest;
import com.prodigalgal.ircs.content.people.dto.PeopleAdminDtos.PersonRequest;
import com.prodigalgal.ircs.content.people.dto.PeopleAdminDtos.PersonResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/directors")
@RequiredArgsConstructor
public class DirectorAdminController {

    private final PeopleAdminService service;

    @GetMapping
    public ResponseEntity<PageEnvelope<PersonResponse>> getAll(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String name) {
        return ResponseEntity.ok(PageEnvelope.from(service.findAll(PeopleResource.DIRECTORS, pageable, name)));
    }

    @GetMapping("/all")
    public ResponseEntity<List<PersonResponse>> getAllNoPaging() {
        return ResponseEntity.ok(service.findAllSortedByName(PeopleResource.DIRECTORS));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PersonResponse> getOne(@PathVariable UUID id) {
        return service.findOne(PeopleResource.DIRECTORS, id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PersonResponse> create(@Valid @RequestBody PersonRequest request) {
        PersonResponse result = service.create(PeopleResource.DIRECTORS, request);
        return ResponseEntity.created(URI.create("/api/v1/directors/" + result.id())).body(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PersonResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PersonRequest request) {
        return ResponseEntity.ok(service.update(PeopleResource.DIRECTORS, id, request));
    }

    @PatchMapping(value = "/{id}", consumes = {"application/json", "application/merge-patch+json"})
    public ResponseEntity<PersonResponse> patch(
            @PathVariable UUID id,
            @Valid @RequestBody PersonPatchRequest request) {
        return ResponseEntity.ok(service.patch(PeopleResource.DIRECTORS, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(PeopleResource.DIRECTORS, id);
        return ResponseEntity.noContent().build();
    }
}
