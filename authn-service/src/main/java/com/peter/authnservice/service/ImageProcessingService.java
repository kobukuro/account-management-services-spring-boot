package com.peter.authnservice.service;

import com.peter.authnservice.domain.dto.ProcessedImage;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;


public interface ImageProcessingService {
    void validateFileSize(MultipartFile file);

    void validateContentType(MultipartFile file);

    void validateFileExtension(String filename);

    void validateMagicNumber(MultipartFile file) throws IOException;

    ProcessedImage processImage(MultipartFile file) throws IOException;
}
