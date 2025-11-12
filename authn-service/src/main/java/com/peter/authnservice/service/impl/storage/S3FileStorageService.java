package com.peter.authnservice.service.impl.storage;

import com.peter.authnservice.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

@Service
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    public S3FileStorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public String uploadFile(String key, InputStream inputStream, String contentType, long contentLength) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .acl(ObjectCannedACL.PUBLIC_READ) // Make the file publicly readable
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));

        // Return the public URL
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);
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
        // URL format: https://{bucket}.s3.{region}.amazonaws.com/{key}
        // or: https://s3.{region}.amazonaws.com/{bucket}/{key}
        if (url == null || url.isEmpty()) {
            return null;
        }

        // Handle bucket.s3.region.amazonaws.com format
        String pattern1 = String.format("https://%s.s3.%s.amazonaws.com/", bucketName, region);
        if (url.startsWith(pattern1)) {
            return url.substring(pattern1.length());
        }

        // Handle s3.region.amazonaws.com/bucket format
        String pattern2 = String.format("https://s3.%s.amazonaws.com/%s/", region, bucketName);
        if (url.startsWith(pattern2)) {
            return url.substring(pattern2.length());
        }

        // If no pattern matches, return null
        return null;
    }
}
