package com.peter.authnservice.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GoogleOAuthLoginRequest(
        @NotBlank(message = "Authorization code is required")
        String authorizationCode,

        @NotBlank(message = "Redirect URI is required")
        @Pattern(
                regexp = "^https://[a-zA-Z0-9.-]+(?::[0-9]+)?(?:/\\S*)?$",
                message = "Redirect URI must be a valid HTTPS URL"
        )
        String redirectUri
) {}
