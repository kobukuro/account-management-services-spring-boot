package com.peter.authnservice.service;

import java.io.InputStream;
import java.time.Duration;

public interface FileStorageService {
    /**
     * Upload a file to storage with private access control.
     * The file is stored with PRIVATE ACL and requires presigned URLs for access.
     *
     * @param key the object key (path) in storage
     * @param inputStream the file input stream
     * @param contentType the content type of the file
     * @param contentLength the size of the file in bytes
     * @return the object key of the uploaded file (not a public URL)
     */
    String uploadFile(String key, InputStream inputStream, String contentType, long contentLength);

    /**
     * Generate a temporary presigned URL for secure access to a private file.
     * The URL will expire after the specified duration.
     *
     * @param key the object key (path) in storage
     * @param expiration how long the URL should remain valid
     * @return a temporary presigned URL that expires after the given duration
     */
    String generatePresignedUrl(String key, Duration expiration);

    /**
     * Delete a file from storage
     *
     * @param key the object key (path) in storage
     */
    void deleteFile(String key);

    /**
     * Extract the object key from a full URL or return the key if already a key.
     * This method handles both presigned URLs and regular S3 URLs.
     *
     * @param url the full URL of the file or the object key
     * @return the object key
     */
    String extractKeyFromUrl(String url);
}
