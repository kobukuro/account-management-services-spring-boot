package com.peter.authnservice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

public record PasswordResetConfirmRequest(
        @NotEmpty(message = "token field cannot be empty")
        String token,

        @Schema(
                description = "New password - must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one number, one special character and no whitespace",
                example = "Password123!"
        )
        @NotEmpty(message = "password field cannot be empty")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d])[A-Za-z\\d\\S]{8,}$",
                message = "Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one number, one special character and no whitespace"
        )
        String password) {
}
