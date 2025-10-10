package com.peter.authnservice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

public record PasswordChangeRequest(
        @Schema(
                description = "User's current password",
                example = "currentPassword123"
        )
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @Schema(
                description = "User's new password - must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one number, and one special character",
                example = "Password123!"
        )
        @NotEmpty(message = "password field cannot be empty")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d\\s]).{8,}$",
                message = "Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one number, and one special character"
        )
        String newPassword) {
}
