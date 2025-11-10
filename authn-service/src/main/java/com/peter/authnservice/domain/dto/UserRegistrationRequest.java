package com.peter.authnservice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public record UserRegistrationRequest(
        @Schema(
                description = "User's first name",
                example = "John"
        )
        @NotEmpty(message = "first name field cannot be empty")
        @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
        String firstName,

        @Schema(
                description = "User's last name",
                example = "Doe"
        )
        @NotEmpty(message = "last name field cannot be empty")
        @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
        String lastName,

        @Schema(
                description = "User's email address",
                example = "john.doe@example.com"
        )
        @NotEmpty(message = "email field cannot be empty")
        @Email(message = "Invalid email format")
        String email,

        @Schema(
                description = "User's password - must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one number, and one special character",
                example = "Password123!"
        )
        @NotEmpty(message = "password field cannot be empty")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d\\s]).{8,}$",
                message = "Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one number, and one special character"
        )
        String password) {
}
