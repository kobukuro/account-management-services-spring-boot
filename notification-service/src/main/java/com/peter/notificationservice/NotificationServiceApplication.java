package com.peter.notificationservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peter.notificationservice.domain.event.UserRegistrationEvent;
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
    @KafkaListener(topics = "user_registration")
    public void handleNotification(UserRegistrationEvent userRegistrationEvent, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, @Header(KafkaHeaders.OFFSET) long offset) throws JsonProcessingException {
        log.info("Received: {} from {} offset {}", new ObjectMapper().writeValueAsString(userRegistrationEvent), topic, offset);
        notificationService.processNotification(userRegistrationEvent);
    }

    @DltHandler
    public void listenDLT(UserRegistrationEvent userRegistrationEvent, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, @Header(KafkaHeaders.OFFSET) long offset) throws JsonProcessingException {
        log.info("DLT Received : {} , from {} , offset {}", new ObjectMapper().writeValueAsString(userRegistrationEvent), topic, offset);
    }
}

