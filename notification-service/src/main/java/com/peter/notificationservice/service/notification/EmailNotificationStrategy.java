package com.peter.notificationservice.service.notification;

import com.peter.notificationservice.domain.event.Event;
import com.peter.notificationservice.service.EmailService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailNotificationStrategy implements NotificationStrategy {
    private final EmailService emailService;

    public EmailNotificationStrategy(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public boolean canHandle(Event event) {
        return event.userDetails() != null &&
                event.userDetails().email() != null &&
                !event.userDetails().email().isEmpty();
    }

    @Override
    public void notify(Event event) {
        String email = event.userDetails().email();
        String subject = event.email().subject();
        String templateName = event.email().templateName();
        Map<String, Object> templateModel = event.email().model();
        emailService.sendHtmlEmail(
                email,
                subject, // email subject
                templateName,  // Thymeleaf template name
                templateModel
        );
    }
}
