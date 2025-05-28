package com.peter.authnservice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserLoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Schema(
                description = "User's email address",
                example = "john.doe@example.com"
        )
        String email,
        @NotBlank(message = "Password is required")
        @Schema(
                description = "User's password",
                example = "password"
        )
        String password) {
}
