package com.peter.authnservice.service.storage;

import com.peter.authnservice.service.impl.storage.S3FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("ci")
class S3FileStorageServiceTest {

    @Mock
    private S3Client s3Client;

    private S3FileStorageService s3FileStorageService;

    @Value("${aws.s3.bucket-name}")
    private String BUCKET_NAME;

    @Value("${aws.s3.region}")
    private String REGION;

    @BeforeEach
    void setUp() {
        s3FileStorageService = new S3FileStorageService(s3Client);
        ReflectionTestUtils.setField(s3FileStorageService, "bucketName", BUCKET_NAME);
        ReflectionTestUtils.setField(s3FileStorageService, "region", REGION);
    }

    @Test
    void uploadFile_shouldUploadToS3AndReturnUrl() {
        // Given
        String key = "users/123/profile.jpg";
        byte[] fileContent = "test image content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(fileContent);
        String contentType = "image/jpeg";
        long contentLength = fileContent.length;

        // When
        String result = s3FileStorageService.uploadFile(key, inputStream, contentType, contentLength);

        // Then
        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);

        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

        PutObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(BUCKET_NAME, capturedRequest.bucket());
        assertEquals(key, capturedRequest.key());
        assertEquals(contentType, capturedRequest.contentType());
        assertEquals(contentLength, capturedRequest.contentLength());
        assertEquals(ObjectCannedACL.PUBLIC_READ, capturedRequest.acl());

        String expectedUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", BUCKET_NAME, REGION, key);
        assertEquals(expectedUrl, result);
    }

    @Test
    void deleteFile_shouldDeleteFromS3() {
        // Given
        String key = "users/123/profile.jpg";

        // When
        s3FileStorageService.deleteFile(key);

        // Then
        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());

        DeleteObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(BUCKET_NAME, capturedRequest.bucket());
        assertEquals(key, capturedRequest.key());
    }

    @Test
    void extractKeyFromUrl_shouldExtractKeyFromBucketSubdomainFormat() {
        // Given
        String url = String.format("https://%s.s3.%s.amazonaws.com/users/123/profile.jpg", BUCKET_NAME, REGION);

        // When
        String result = s3FileStorageService.extractKeyFromUrl(url);

        // Then
        assertEquals("users/123/profile.jpg", result);
    }

    @Test
    void extractKeyFromUrl_shouldExtractKeyFromPathStyleFormat() {
        // Given
        String url = String.format("https://s3.%s.amazonaws.com/%s/users/123/profile.jpg", REGION, BUCKET_NAME);

        // When
        String result = s3FileStorageService.extractKeyFromUrl(url);

        // Then
        assertEquals("users/123/profile.jpg", result);
    }

    @Test
    void extractKeyFromUrl_shouldReturnNullForNullUrl() {
        // When
        String result = s3FileStorageService.extractKeyFromUrl(null);

        // Then
        assertNull(result);
    }

    @Test
    void extractKeyFromUrl_shouldReturnNullForEmptyUrl() {
        // When
        String result = s3FileStorageService.extractKeyFromUrl("");

        // Then
        assertNull(result);
    }

    @Test
    void extractKeyFromUrl_shouldReturnNullForInvalidUrl() {
        // Given
        String url = "https://example.com/some/path";

        // When
        String result = s3FileStorageService.extractKeyFromUrl(url);

        // Then
        assertNull(result);
    }
}
