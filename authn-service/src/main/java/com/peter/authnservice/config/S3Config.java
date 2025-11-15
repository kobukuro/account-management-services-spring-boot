package com.peter.authnservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * AWS S3 configuration with support for both real AWS S3 and LocalStack for local development.
 * <p>
 * Production Mode (AWS_S3_ENDPOINT not set):
 * - Uses DefaultCredentialsProvider which automatically resolves credentials from:
 *   1. Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
 *   2. Java system properties
 *   3. Web Identity Token credentials
 *   4. Shared credentials file (~/.aws/credentials)
 *   5. ECS container credentials
 *   6. EC2 instance profile credentials (recommended for production)
 * <p>
 * Local Development Mode (AWS_S3_ENDPOINT set to <a href="http://localhost:4566">http://localhost:4566</a>):
 * - Connects to LocalStack for S3 simulation
 * - Uses static credentials (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY from environment)
 * - Enables path-style access (required by LocalStack)
 * - No real AWS credentials needed - use dummy values like "test"/"test"
 */
@Configuration
public class S3Config {

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.endpoint:}")
    private String endpoint;

    @Value("${aws.s3.access-key-id:}")
    private String accessKeyId;

    @Value("${aws.s3.secret-access-key:}")
    private String secretAccessKey;

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region));

        // LocalStack mode: custom endpoint with static credentials
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint))
                    .forcePathStyle(true); // Required for LocalStack

            // Use static credentials if provided (for LocalStack)
            if (accessKeyId != null && !accessKeyId.isEmpty() &&
                    secretAccessKey != null && !secretAccessKey.isEmpty()) {
                builder.credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                ));
            }
        }
        // Production mode: uses DefaultCredentialsProvider (no explicit configuration needed)

        return builder.build();
    }

    /**
     * S3Presigner bean for generating presigned URLs for secure, temporary access to private S3 objects.
     * Presigned URLs allow controlled access to private files without making them publicly accessible.
     */
    @Bean
    public S3Presigner s3Presigner() {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(region));

        // LocalStack mode: custom endpoint with static credentials and path-style access
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(
                            S3Configuration.builder()
                                    .pathStyleAccessEnabled(true)  // Required for LocalStack presigned URLs
                                    .build()
                    );

            // Use static credentials if provided (for LocalStack)
            if (accessKeyId != null && !accessKeyId.isEmpty() &&
                    secretAccessKey != null && !secretAccessKey.isEmpty()) {
                builder.credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                ));
            }
        }
        // Production mode: uses DefaultCredentialsProvider (no explicit configuration needed)

        return builder.build();
    }
}
