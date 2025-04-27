package com.peter.notificationservice;

import com.peter.notificationservice.domain.event.UserRegistrationEvent;
import com.peter.notificationservice.service.NotificationService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;

@SpringBootApplication
public class NotificationServiceApplication {
    private final NotificationService notificationService;

    public NotificationServiceApplication(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }


    @KafkaListener(topics = "user_registration")
    public void handleNotification(UserRegistrationEvent userRegistrationEvent) {
        notificationService.processNotification(userRegistrationEvent);
    }
}

