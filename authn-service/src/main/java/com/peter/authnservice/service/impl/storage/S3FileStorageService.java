package com.peter.authnservice.service.impl.storage;

import com.peter.authnservice.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.time.Duration;

@Service
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    public S3FileStorageService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @Override
    public String uploadFile(String key, InputStream inputStream, String contentType, long contentLength) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .acl(ObjectCannedACL.PRIVATE) // ✅ SECURITY: Use PRIVATE ACL - requires presigned URLs for access
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));

        // Return the object key (not a public URL) - presigned URLs must be generated on-demand
        return key;
    }

    @Override
    public String generatePresignedUrl(String key, Duration expiration) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    @Override
    public void deleteFile(String key) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
    }

    @Override
    public String extractKeyFromUrl(String url) {
        // Handle both presigned URLs and regular S3 URLs
        // URL formats:
        // - Presigned: https://{bucket}.s3.{region}.amazonaws.com/{key}?X-Amz-Algorithm=...
        // - Regular: https://{bucket}.s3.{region}.amazonaws.com/{key}
        // - Path-style: https://s3.{region}.amazonaws.com/{bucket}/{key}
        // - Also supports http:// for compatibility
        // - Already a key: profile-pictures/{userId}/{filename}

        if (url == null || url.isEmpty()) {
            return null;
        }

        // If it's already a key (no http:// or https://), return as-is
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return url;
        }

        // Remove query parameters from presigned URLs
        String urlWithoutQuery = url.split("\\?")[0];

        // Extract protocol and rest of URL
        String protocol = urlWithoutQuery.startsWith("https://") ? "https://" : "http://";

        // Handle bucket.s3.region.amazonaws.com format
        String pattern1 = String.format("%s%s.s3.%s.amazonaws.com/", protocol, bucketName, region);
        if (urlWithoutQuery.startsWith(pattern1)) {
            return urlWithoutQuery.substring(pattern1.length());
        }

        // Handle s3.region.amazonaws.com/bucket format
        String pattern2 = String.format("%ss3.%s.amazonaws.com/%s/", protocol, region, bucketName);
        if (urlWithoutQuery.startsWith(pattern2)) {
            return urlWithoutQuery.substring(pattern2.length());
        }

        // If no pattern matches, return null
        return null;
    }
}
