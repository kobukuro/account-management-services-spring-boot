package com.peter.authnservice.service.impl;

import com.peter.authnservice.domain.dto.ProcessedImage;
import com.peter.authnservice.service.ImageProcessingService;
import org.apache.commons.io.IOUtils;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

@Service
public class ImageProcessingServiceImpl implements ImageProcessingService {

    @Value("${file.upload.max-size-mb}")
    private int maxSizeMb;

    @Value("${file.upload.profile-picture.max-width}")
    private int maxWidth;

    @Value("${file.upload.profile-picture.max-height}")
    private int maxHeight;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg",
            ".jpeg",
            ".png",
            ".webp"
    );

    // Magic numbers for file type validation
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] WEBP_MAGIC = {0x52, 0x49, 0x46, 0x46}; // "RIFF"

    /**
     * Validate file size
     */
    @Override
    public void validateFileSize(MultipartFile file) {
        long maxSizeBytes = (long) maxSizeMb * 1024 * 1024;
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException(
                    String.format("File size exceeds maximum allowed size of %d MB", maxSizeMb)
            );
        }
    }

    /**
     * Validate file content type
     */
    @Override
    public void validateContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("File content type is missing");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Invalid file type. Only JPEG, PNG, and WebP images are allowed"
            );
        }
    }

    /**
     * Validate file extension
     */
    @Override
    public void validateFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Filename is missing");
        }

        String lowerFilename = filename.toLowerCase();
        boolean hasValidExtension = ALLOWED_EXTENSIONS.stream()
                .anyMatch(lowerFilename::endsWith);

        if (!hasValidExtension) {
            throw new IllegalArgumentException(
                    "Invalid file extension. Only .jpg, .jpeg, .png, and .webp are allowed"
            );
        }
    }

    /**
     * Validate file magic number to prevent spoofed file types
     */
    @Override
    public void validateMagicNumber(MultipartFile file) throws IOException {
        byte[] fileBytes = IOUtils.toByteArray(file.getInputStream());
        if (fileBytes.length < 4) {
            throw new IllegalArgumentException("File is too small to be a valid image");
        }

        boolean isValid = startsWithMagicNumber(fileBytes, JPEG_MAGIC) ||
                startsWithMagicNumber(fileBytes, PNG_MAGIC) ||
                startsWithMagicNumber(fileBytes, WEBP_MAGIC);

        if (!isValid) {
            throw new IllegalArgumentException("File content does not match a valid image format");
        }
    }

    private boolean startsWithMagicNumber(byte[] fileBytes, byte[] magicNumber) {
        if (fileBytes.length < magicNumber.length) {
            return false;
        }
        for (int i = 0; i < magicNumber.length; i++) {
            if (fileBytes[i] != magicNumber[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Process and resize image if needed
     * Returns processed image as InputStream and the processed size
     */
    public ProcessedImage processImage(MultipartFile file) throws IOException {
        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        if (originalImage == null) {
            throw new IllegalArgumentException("Unable to read image file");
        }

        // Resize if image is larger than max dimensions
        BufferedImage processedImage = originalImage;
        if (originalImage.getWidth() > maxWidth || originalImage.getHeight() > maxHeight) {
            processedImage = Scalr.resize(
                    originalImage,
                    Scalr.Method.QUALITY,
                    Scalr.Mode.FIT_TO_WIDTH,
                    maxWidth,
                    maxHeight,
                    Scalr.OP_ANTIALIAS
            );
        }

        // Convert to WebP format for optimal storage (or keep as JPEG for compatibility)
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(processedImage, "png", outputStream); // Using PNG for compatibility
        byte[] imageBytes = outputStream.toByteArray();

        return new ProcessedImage(
                new ByteArrayInputStream(imageBytes),
                imageBytes.length,
                "image/png"
        );
    }
}
