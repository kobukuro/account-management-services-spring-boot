package com.peter.authnservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Kafka topics.
 * Externalizes topic names to allow different configurations per environment.
 */
@ConfigurationProperties(prefix = "kafka.topics")
public record KafkaTopicConfig(
    String userRegistration,
    String resendActivation,
    String passwordReset,
    String passwordResetConfirm,
    String passwordChange
) {}
