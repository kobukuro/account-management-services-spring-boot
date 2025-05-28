package com.peter.authnservice.domain.dto;

public record TokenPair(
        String accessToken,
        String refreshToken) {
}
