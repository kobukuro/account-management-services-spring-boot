package com.peter.authnservice.service;

import java.io.InputStream;

public interface FileStorageService {
    /**
     * Upload a file to storage
     *
     * @param key the object key (path) in storage
     * @param inputStream the file input stream
     * @param contentType the content type of the file
     * @param contentLength the size of the file in bytes
     * @return the public URL of the uploaded file
     */
    String uploadFile(String key, InputStream inputStream, String contentType, long contentLength);

    /**
     * Delete a file from storage
     *
     * @param key the object key (path) in storage
     */
    void deleteFile(String key);

    /**
     * Extract the object key from a full URL
     *
     * @param url the full URL of the file
     * @return the object key
     */
    String extractKeyFromUrl(String url);
}
