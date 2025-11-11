package com.peter.authnservice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Schema(
                description = "User's first name",
                example = "John"
        )
        @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
        String firstName,

        @Schema(
                description = "User's last name",
                example = "Doe"
        )
        @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
        String lastName) {
}
