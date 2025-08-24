package com.peter.authnservice.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleOAuthLoginRequest(
        @NotBlank(message = "Authorization code is required")
        String authorizationCode,

        @NotBlank(message = "Redirect URI is required")
        String redirectUri
) {}
