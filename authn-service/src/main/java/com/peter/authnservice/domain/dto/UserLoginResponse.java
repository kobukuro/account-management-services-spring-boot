package com.peter.authnservice.domain.dto;

public record UserLoginResponse(
        String accessToken,
        String refreshToken) {
}
