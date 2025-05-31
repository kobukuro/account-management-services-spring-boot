package com.peter.notificationservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peter.notificationservice.domain.event.Event;
import com.peter.notificationservice.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;

@SpringBootApplication
@Slf4j
public class NotificationServiceApplication {
    private final NotificationService notificationService;

    public NotificationServiceApplication(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
    @RetryableTopic
    @KafkaListener(topics = {"user_registration", "password_reset"})
    public void handleNotification(Event event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, @Header(KafkaHeaders.OFFSET) long offset) throws JsonProcessingException {
        log.info("Received: {} from {} offset {}", new ObjectMapper().writeValueAsString(event), topic, offset);
        notificationService.processNotification(event);
    }

    @DltHandler
    public void listenDLT(Event event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, @Header(KafkaHeaders.OFFSET) long offset) throws JsonProcessingException {
        log.info("DLT Received : {} , from {} , offset {}", new ObjectMapper().writeValueAsString(event), topic, offset);
    }
}

