package com.peter.authnservice.domain.dto;

import java.io.InputStream;

/**
 * Record class to hold processed image data
 */
public record ProcessedImage(InputStream inputStream, long size, String contentType) {
}
