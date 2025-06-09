package com.peter.authnservice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.ZonedDateTime;
import java.util.UUID;

public record UserRegistrationResponse(
        UUID id,
        @Schema(
                example = "john.doe@example.com"
        )
        String email,
        ZonedDateTime created_at
) {
}
