package com.peter.authnservice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.ZonedDateTime;
import java.util.UUID;

public record UpdateProfileResponse(
        @Schema(
                description = "User's unique identifier",
                example = "123e4567-e89b-12d3-a456-426614174000"
        )
        UUID id,

        @Schema(
                description = "User's first name",
                example = "John"
        )
        String firstName,

        @Schema(
                description = "User's last name",
                example = "Doe"
        )
        String lastName,

        @Schema(
                description = "Timestamp when the profile was last updated",
                example = "2025-11-09T10:30:00Z"
        )
        ZonedDateTime lastUpdatedAt) {
}
