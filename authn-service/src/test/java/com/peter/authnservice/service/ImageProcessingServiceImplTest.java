package com.peter.authnservice.service;

import com.peter.authnservice.domain.dto.ProcessedImage;
import com.peter.authnservice.service.impl.ImageProcessingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("ci")
class ImageProcessingServiceImplTest {

    @Autowired
    private ImageProcessingServiceImpl imageProcessingService;

    @Value("${file.upload.max-size-mb}")
    private int maxSizeMb;

    @Value("${file.upload.profile-picture.max-width}")
    private int maxWidth;

    @Value("${file.upload.profile-picture.max-height}")
    private int maxHeight;

    @BeforeEach
    void setUp() {
        // No need for manual setup, Spring will inject the service with proper @Value fields
    }

    @Test
    void validateFileSize_shouldPassForValidSize() {
        // Given
        byte[] content = new byte[1024 * 1024]; // 1MB
        MultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", content);

        // When/Then
        assertDoesNotThrow(() -> imageProcessingService.validateFileSize(file));
    }

    @Test
    void validateFileSize_shouldThrowExceptionForOversizedFile() {
        // Given
        byte[] content = new byte[(maxSizeMb + 1) * 1024 * 1024]; // Exceeds max size
        MultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", content);

        // When/Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> imageProcessingService.validateFileSize(file)
        );
        assertTrue(exception.getMessage().contains("exceeds maximum allowed size"));
    }

    @Test
    void validateContentType_shouldPassForJpeg() {
        // Given
        MultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[0]);

        // When/Then
        assertDoesNotThrow(() -> imageProcessingService.validateContentType(file));
    }

    @Test
    void validateContentType_shouldPassForPng() {
        // Given
        MultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[0]);

        // When/Then
        assertDoesNotThrow(() -> imageProcessingService.validateContentType(file));
    }

    @Test
    void validateContentType_shouldPassForWebp() {
        // Given
        MultipartFile file = new MockMultipartFile("file", "test.webp", "image/webp", new byte[0]);

        // When/Then
        assertDoesNotThrow(() -> imageProcessingService.validateContentType(file));
    }

    @Test
    void validateContentType_shouldThrowExceptionForNullContentType() {
        // Given
        MultipartFile file = new MockMultipartFile("file", "test.jpg", null, new byte[0]);

        // When/Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> imageProcessingService.validateContentType(file)
        );
        assertEquals("File content type is missing", exception.getMessage());
    }

    @Test
    void validateContentType_shouldThrowExceptionForInvalidContentType() {
        // Given
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", new byte[0]);

        // When/Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> imageProcessingService.validateContentType(file)
        );
        assertTrue(exception.getMessage().contains("Invalid file type"));
    }

    @Test
    void validateFileExtension_shouldPassForJpg() {
        // When/Then
        assertDoesNotThrow(() -> imageProcessingService.validateFileExtension("test.jpg"));
    }

    @Test
    void validateFileExtension_shouldPassForJpeg() {
        // When/Then
        assertDoesNotThrow(() -> imageProcessingService.validateFileExtension("test.jpeg"));
    }

    @Test
    void validateFileExtension_shouldPassForPng() {
        // When/Then
        assertDoesNotThrow(() -> imageProcessingService.validateFileExtension("test.png"));
    }

    @Test
    void validateFileExtension_shouldPassForWebp() {
        // When/Then
        assertDoesNotThrow(() -> imageProcessingService.validateFileExtension("test.webp"));
    }

    @Test
    void validateFileExtension_shouldPassForUppercaseExtension() {
        // When/Then
        assertDoesNotThrow(() -> imageProcessingService.validateFileExtension("test.JPG"));
    }

    @Test
    void validateFileExtension_shouldThrowExceptionForNullFilename() {
        // When/Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> imageProcessingService.validateFileExtension(null)
        );
        assertEquals("Filename is missing", exception.getMessage());
    }

    @Test
    void validateFileExtension_shouldThrowExceptionForEmptyFilename() {
        // When/Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> imageProcessingService.validateFileExtension("")
        );
        assertEquals("Filename is missing", exception.getMessage());
    }

    @Test
    void validateFileExtension_shouldThrowExceptionForInvalidExtension() {
        // When/Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> imageProcessingService.validateFileExtension("test.txt")
        );
        assertTrue(exception.getMessage().contains("Invalid file extension"));
    }

    @Test
    void validateMagicNumber_shouldPassForJpegFile() {
        // Given - JPEG magic number: FF D8 FF
        byte[] jpegContent = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};

        // When/Then
        assertDoesNotThrow(() -> imageProcessingService.validateMagicNumber(jpegContent));
    }

    @Test
    void validateMagicNumber_shouldPassForPngFile() {
        // Given - PNG magic number: 89 50 4E 47
        byte[] pngContent = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

        // When/Then
        assertDoesNotThrow(() -> imageProcessingService.validateMagicNumber(pngContent));
    }

    @Test
    void validateMagicNumber_shouldPassForWebpFile() {
        // Given - WebP magic number: 52 49 46 46 (RIFF)
        byte[] webpContent = new byte[]{0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00};

        // When/Then
        assertDoesNotThrow(() -> imageProcessingService.validateMagicNumber(webpContent));
    }

    @Test
    void validateMagicNumber_shouldThrowExceptionForTooSmallFile() {
        // Given
        byte[] content = new byte[]{0x01, 0x02}; // Only 2 bytes

        // When/Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> imageProcessingService.validateMagicNumber(content)
        );
        assertEquals("File is too small to be a valid image", exception.getMessage());
    }

    @Test
    void validateMagicNumber_shouldHandleFileWithInvalidMagicBytes() {
        // Given - File with exactly 4 bytes that don't match any valid magic number
        // This passes the minimum size check (4 bytes) and tests all three magic number checks
        // The startsWithMagicNumber method is called for JPEG (3 bytes), PNG (4 bytes), and WEBP (4 bytes)
        byte[] content = new byte[]{0x01, 0x02, 0x03, 0x04}; // 4 bytes - not a valid magic number

        // When/Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> imageProcessingService.validateMagicNumber(content)
        );
        assertEquals("File content does not match a valid image format", exception.getMessage());
    }

    @Test
    void validateMagicNumber_shouldThrowExceptionForInvalidMagicNumber() {
        // Given - Invalid magic number
        byte[] content = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00};

        // When/Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> imageProcessingService.validateMagicNumber(content)
        );
        assertEquals("File content does not match a valid image format", exception.getMessage());
    }

    @Test
    void processImage_shouldProcessImageWithoutResizing() throws IOException {
        // Given - Create a small test image (smaller than max dimensions)
        // Note: We need to create a proper PNG image with valid format
        int originalWidth = 100;
        int originalHeight = 100;
        BufferedImage testImage = new BufferedImage(originalWidth, originalHeight, BufferedImage.TYPE_INT_RGB);

        // Fill with some color to make it a valid image
        for (int x = 0; x < originalWidth; x++) {
            for (int y = 0; y < originalHeight; y++) {
                testImage.setRGB(x, y, 0xFF0000); // Red color
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "png", baos);
        byte[] imageBytes = baos.toByteArray();

        // When
        ProcessedImage result = imageProcessingService.processImage(imageBytes);

        // Then
        assertNotNull(result);
        assertNotNull(result.inputStream());
        assertTrue(result.size() > 0);
        assertEquals("image/png", result.contentType());

        // Read the processed image to verify dimensions using try-with-resources
        byte[] processedBytes;
        try (InputStream inputStream = result.inputStream()) {
            processedBytes = inputStream.readAllBytes();
        }
        assertTrue(processedBytes.length > 0, "Processed image bytes should not be empty");

        BufferedImage processedImage;
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(processedBytes)) {
            processedImage = ImageIO.read(byteArrayInputStream);
        }
        assertNotNull(processedImage, "Processed image should be readable");

        // Verify no resizing occurred - dimensions should remain the same
        assertEquals(originalWidth, processedImage.getWidth(),
                "Width should remain unchanged when image is smaller than max dimensions");
        assertEquals(originalHeight, processedImage.getHeight(),
                "Height should remain unchanged when image is smaller than max dimensions");
    }

    @Test
    void processImage_shouldResizeLargeImage() throws IOException {
        // Given - Create a large test image (larger than max dimensions)
        BufferedImage testImage = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "png", baos);
        byte[] imageBytes = baos.toByteArray();

        // When
        ProcessedImage result = imageProcessingService.processImage(imageBytes);

        // Then
        assertNotNull(result);
        assertNotNull(result.inputStream());
        assertTrue(result.size() > 0);
        assertEquals("image/png", result.contentType());

        // Verify the image was resized using try-with-resources
        byte[] processedBytes;
        try (InputStream inputStream = result.inputStream()) {
            processedBytes = inputStream.readAllBytes();
        }

        BufferedImage processedImage;
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(processedBytes)) {
            processedImage = ImageIO.read(byteArrayInputStream);
        }
        assertNotNull(processedImage);
        assertTrue(processedImage.getWidth() <= maxWidth);
        assertTrue(processedImage.getHeight() <= maxHeight);
    }

    @Test
    void processImage_shouldThrowExceptionForInvalidImageFile() {
        // Given - Invalid image data
        byte[] invalidContent = "not an image".getBytes();

        // When/Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> imageProcessingService.processImage(invalidContent)
        );
        assertEquals("Unable to read image file", exception.getMessage());
    }

    @Test
    void processImage_shouldResizeImageWithOnlyHeightExceedingMax() throws IOException {
        // Given - Create an image where only height exceeds (width OK, height too tall)
        // Using a small width but large height to test the OR condition where height > maxHeight
        BufferedImage testImage = new BufferedImage(100, maxHeight + 100, BufferedImage.TYPE_INT_RGB);
        int originalHeight = testImage.getHeight();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "png", baos);
        byte[] imageBytes = baos.toByteArray();

        // When
        ProcessedImage result = imageProcessingService.processImage(imageBytes);

        // Then
        assertNotNull(result);
        assertNotNull(result.inputStream());
        assertTrue(result.size() > 0);
        assertEquals("image/png", result.contentType());

        // Verify the resize condition was entered (covers the OR branch where only height exceeds)
        // The test ensures originalImage.getHeight() > maxHeight triggered the resize branch
        byte[] processedBytes;
        try (InputStream inputStream = result.inputStream()) {
            processedBytes = inputStream.readAllBytes();
        }

        BufferedImage processedImage;
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(processedBytes)) {
            processedImage = ImageIO.read(byteArrayInputStream);
        }
        assertNotNull(processedImage);

        // Verify the original height exceeded max (to ensure we entered the resize branch)
        assertTrue(originalHeight > maxHeight, "Original height should exceed max to enter resize branch");

        // Verify the processed image height is now within the allowed range
        assertTrue(processedImage.getHeight() <= maxHeight,
                "Processed image height should be within max height after resizing");
        assertTrue(processedImage.getWidth() <= maxWidth,
                "Processed image width should be within max width after resizing");
    }

    @Test
    void startsWithMagicNumber_shouldReturnFalseWhenFileBytesAreShorterThanMagicNumber() throws Exception {
        // This test uses reflection to directly test the private method to cover the branch
        // where fileBytes.length < magicNumber.length (line 116-117)

        // Given
        byte[] fileBytes = new byte[]{0x01, 0x02}; // Only 2 bytes
        byte[] magicNumber = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}; // JPEG magic = 3 bytes

        // When - Use reflection to access private method
        Method method = ImageProcessingServiceImpl.class.getDeclaredMethod(
                "startsWithMagicNumber", byte[].class, byte[].class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(imageProcessingService, fileBytes, magicNumber);

        // Then - Should return false because fileBytes (2) < magicNumber (3)
        assertFalse(result, "Should return false when fileBytes are shorter than magicNumber");
    }
}
