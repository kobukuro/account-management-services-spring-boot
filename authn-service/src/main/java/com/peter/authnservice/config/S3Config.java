package com.peter.authnservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * AWS S3 configuration using DefaultCredentialsProvider for enhanced security.
 * This approach automatically resolves credentials from multiple sources in order:
 * 1. Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
 * 2. Java system properties
 * 3. Web Identity Token credentials
 * 4. Shared credentials file (~/.aws/credentials)
 * 5. ECS container credentials
 * 6. EC2 instance profile credentials (recommended for production)
 * <p>
 * This eliminates the need for static credentials and follows AWS security best practices.
 */
@Configuration
public class S3Config {

    @Value("${aws.s3.region}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                // Uses DefaultCredentialsProvider by default - automatically selects appropriate credential source
                .build();
    }

    /**
     * S3Presigner bean for generating presigned URLs for secure, temporary access to private S3 objects.
     * Presigned URLs allow controlled access to private files without making them publicly accessible.
     */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                // Uses DefaultCredentialsProvider by default - automatically selects appropriate credential source
                .build();
    }
}
