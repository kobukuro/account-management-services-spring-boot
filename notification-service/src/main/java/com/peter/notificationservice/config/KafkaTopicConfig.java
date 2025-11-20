package com.peter.notificationservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Kafka topics.
 * Externalizes topic names to allow different configurations per environment.
 */
@ConfigurationProperties(prefix = "kafka.topics")
@Validated
public record KafkaTopicConfig(
        @NotBlank String userRegistration,
        @NotBlank String resendActivation,
        @NotBlank String passwordReset,
        @NotBlank String passwordResetConfirm,
        @NotBlank String passwordChange
) {
}
