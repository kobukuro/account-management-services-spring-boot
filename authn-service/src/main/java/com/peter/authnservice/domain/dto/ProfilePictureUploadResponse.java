package com.peter.authnservice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProfilePictureUploadResponse(
        @Schema(
                description = "URL of the uploaded profile picture",
                example = "https://bucket-name.s3.us-east-1.amazonaws.com/profile-pictures/123e4567-e89b-12d3-a456-426614174000/abc123.png"
        )
        String profilePictureUrl
) {
}
