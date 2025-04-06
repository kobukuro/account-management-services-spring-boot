package com.peter.authnservice.domain.dto;

import jakarta.validation.constraints.NotEmpty;

public record UserActivationRequest(
        @NotEmpty(message = "token field cannot be empty")
        String token) {
}
