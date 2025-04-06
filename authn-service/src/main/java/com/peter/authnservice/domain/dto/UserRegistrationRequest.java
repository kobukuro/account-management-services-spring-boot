package com.peter.authnservice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;


public record UserRegistrationRequest(
        @Schema(
                description = "User's first name",
                example = "John"
        )
        @NotEmpty(message = "first name field cannot be empty")
        String firstName,
        @Schema(
                description = "User's last name",
                example = "Doe"
        )
        @NotEmpty(message = "last name field cannot be empty")
        String lastName,
        @Schema(
                description = "User's email address",
                example = "john.doe@example.com"
        )
        @NotEmpty(message = "email field cannot be empty")
        @Email(message = "Invalid email format")
        String email,
        @Schema(
                description = "User's password",
                example = "password"
        )
        @NotEmpty(message = "password field cannot be empty")
        String password) {
}
