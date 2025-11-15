package com.peter.authnservice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProfilePictureUploadResponse(
        @Schema(
                description = "Presigned URL for accessing the uploaded profile picture (temporary access link)",
                example = "https://bucket-name.s3.us-east-1.amazonaws.com/profile-pictures/123e4567-e89b-12d3-a456-426614174000/abc123.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=..."
        )
        String profilePictureUrl
) {
}
