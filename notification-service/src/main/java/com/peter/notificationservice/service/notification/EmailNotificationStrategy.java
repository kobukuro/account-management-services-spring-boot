package com.peter.notificationservice.service.notification;

import com.peter.notificationservice.domain.event.Event;
import com.peter.notificationservice.service.EmailService;
import com.peter.notificationservice.util.TemplateValidator;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailNotificationStrategy implements NotificationStrategy {
    private final EmailService emailService;
    private final TemplateValidator templateValidator;

    public EmailNotificationStrategy(EmailService emailService, TemplateValidator templateValidator) {
        this.emailService = emailService;
        this.templateValidator = templateValidator;
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

        // Validate template model before sending email
        templateValidator.validate(templateName, templateModel);

        emailService.sendHtmlEmail(
                email,
                subject, // email subject
                templateName,  // Thymeleaf template name
                templateModel
        );
    }
}
