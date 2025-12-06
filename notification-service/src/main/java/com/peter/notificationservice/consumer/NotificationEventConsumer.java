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
    public void handleNotification(Event event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, @Header(KafkaHeaders.OFFSET) long offset) throws JsonProcessingException {
        log.info("Received: {} from {} offset {}", objectMapper.writeValueAsString(event), topic, offset);
        notificationService.processNotification(event);
    }

    @DltHandler
    public void listenDLT(Event event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, @Header(KafkaHeaders.OFFSET) long offset) throws JsonProcessingException {
        log.info("DLT Received: {} from {} offset {}", objectMapper.writeValueAsString(event), topic, offset);
    }
}
