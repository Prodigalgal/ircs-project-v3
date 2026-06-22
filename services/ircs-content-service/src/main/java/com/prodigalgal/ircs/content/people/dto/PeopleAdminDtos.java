package com.prodigalgal.ircs.content.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class PeopleAdminDtos {

    private PeopleAdminDtos() {
    }

    public record PersonRequest(
            UUID id,
            @NotBlank(message = "Name cannot be blank.")
            @Size(max = 255, message = "Name cannot exceed 255 characters.")
            String name) {
    }

    public record PersonPatchRequest(
            UUID id,
            @Size(max = 255, message = "Name cannot exceed 255 characters.")
            String name) {
    }

    public record PersonResponse(
            UUID id,
            String name,
            Instant createdAt,
            Instant updatedAt) {
    }
}
