package com.peter.notificationservice.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peter.notificationservice.domain.event.Event;
import com.peter.notificationservice.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer component responsible for consuming notification events from authentication service.
 * <p>
 * This consumer listens to multiple Kafka topics related to user authentication flows and delegates
 * event processing to the {@link NotificationService} for handling email notifications.
 * </p>
 *
 * <h3>Consumed Topics</h3>
 * <ul>
 *   <li>{@code user_registration} - New user registration events</li>
 *   <li>{@code resend_activation} - Account activation resend requests</li>
 *   <li>{@code password_reset} - Password reset request events</li>
 *   <li>{@code password_reset_confirm} - Password reset confirmation events</li>
 *   <li>{@code password_change} - Password change notification events</li>
 * </ul>
 *
 * <h3>Retry Mechanism</h3>
 * The consumer uses Spring Kafka's {@link RetryableTopic} annotation to automatically retry failed
 * message processing. Messages that fail after all retry attempts are sent to a Dead Letter Topic (DLT)
 * for manual inspection and handling.
 *
 * <h3>Error Handling</h3>
 * <ul>
 *   <li>Successfully processed events are logged with topic and offset information</li>
 *   <li>Failed events are automatically retried based on the retry policy</li>
 *   <li>Events that exhaust all retries are sent to the DLT and logged separately</li>
 * </ul>
 *
 * @see NotificationService
 * @see Event
 */
@Component
@Slf4j
public class NotificationEventConsumer {
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public NotificationEventConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @RetryableTopic
    @KafkaListener(topics = {
            "${kafka.topics.user-registration}",
            "${kafka.topics.resend-activation}",
            "${kafka.topics.password-reset}",
            "${kafka.topics.password-reset-confirm}",
            "${kafka.topics.password-change}"
    })
    public void handleNotification(Event event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, @Header(KafkaHeaders.OFFSET) long offset) {
        String eventJson;
        try {
            eventJson = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            eventJson = "<serialization failed>";
            log.warn("Failed to serialize event for logging", e);
        }
        log.info("Received: {} from {} offset {}", eventJson, topic, offset);
        notificationService.processNotification(event);
    }

    @DltHandler
    public void listenDLT(Event event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, @Header(KafkaHeaders.OFFSET) long offset) {
        String eventJson;
        try {
            eventJson = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            eventJson = "<serialization failed>";
            log.warn("Failed to serialize event for logging", e);
        }
        log.info("DLT Received: {} from {} offset {}", eventJson, topic, offset);
    }
}
