package com.peter.notificationservice.service.notification;

import com.peter.notificationservice.domain.event.Email;
import com.peter.notificationservice.domain.event.Event;
import com.peter.notificationservice.domain.event.UserDetails;
import com.peter.notificationservice.exception.TemplateValidationException;
import com.peter.notificationservice.service.EmailService;
import com.peter.notificationservice.util.TemplateValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmailNotificationStrategy.
 */
@ExtendWith(MockitoExtension.class)
class EmailNotificationStrategyTest {

    @Mock
    private EmailService emailService;

    @Mock
    private TemplateValidator templateValidator;

    private EmailNotificationStrategy strategy;

    private Event createEventWithEmail(String emailAddress) {
        UserDetails userDetails = new UserDetails("John", "Doe", emailAddress);
        Email email = new Email("Test Subject", "test-template", Map.of());
        return new Event(userDetails, email);
    }

    @BeforeEach
    void setUp() {
        strategy = new EmailNotificationStrategy(emailService, templateValidator);
    }

    @Test
    void canHandleShouldReturnTrueForValidEvent() {
        // Given
        Event event = createEventWithEmail("test@example.com");

        // When
        boolean result = strategy.canHandle(event);

        // Then
        assertTrue(result);
    }

    @Test
    void canHandleShouldReturnFalseWhenUserDetailsIsNull() {
        // Given
        Email email = new Email("Subject", "template", Map.of());
        Event event = new Event(null, email);

        // When
        boolean result = strategy.canHandle(event);

        // Then
        assertFalse(result);
    }

    @Test
    void canHandleShouldReturnFalseWhenEmailIsNull() {
        // Given
        UserDetails userDetails = new UserDetails("John", "Doe", null);
        Email email = new Email("Subject", "template", Map.of());
        Event event = new Event(userDetails, email);

        // When
        boolean result = strategy.canHandle(event);

        // Then
        assertFalse(result);
    }

    @Test
    void canHandleShouldReturnFalseWhenEmailIsEmpty() {
        // Given
        Event event = createEventWithEmail("");

        // When
        boolean result = strategy.canHandle(event);

        // Then
        assertFalse(result);
    }

    @Test
    void notifyShouldValidateTemplateBeforeSendingEmail() {
        // Given
        UserDetails userDetails = new UserDetails("John", "Doe", "john@example.com");
        Map<String, Object> model = Map.of(
                "firstName", "John",
                "lastName", "Doe",
                "appName", "My App"
        );
        Email email = new Email(
                "Test Subject",
                "email/change-password-email",
                model
        );
        Event event = new Event(userDetails, email);

        doNothing().when(templateValidator).validate(anyString(), anyMap());

        // When
        strategy.notify(event);

        InOrder inOrder = inOrder(templateValidator, emailService);
        // Then - validator should be called before emailService
        inOrder.verify(templateValidator).validate("email/change-password-email", model);
        inOrder.verify(emailService).sendHtmlEmail(
                eq("john@example.com"),
                eq("Test Subject"),
                eq("email/change-password-email"),
                eq(model)
        );
    }

    @Test
    void notifyShouldThrowExceptionWhenValidationFails() {
        // Given
        UserDetails userDetails = new UserDetails("John", "Doe", "john@example.com");
        Map<String, Object> model = Map.of("firstName", "John"); // Missing required variables
        Email email = new Email(
                "Test Subject",
                "email/verification-email",
                model
        );
        Event event = new Event(userDetails, email);

        Set<String> missingVars = Set.of("lastName", "appName", "verificationLink", "expirationHours");
        doThrow(new TemplateValidationException("email/verification-email", missingVars))
                .when(templateValidator).validate(anyString(), anyMap());

        // When/Then
        TemplateValidationException exception = assertThrows(
                TemplateValidationException.class,
                () -> strategy.notify(event)
        );

        assertEquals("email/verification-email", exception.getTemplateName());
        assertEquals(missingVars, exception.getMissingVariables());

        // Email service should not be called when validation fails
        verify(emailService, never()).sendHtmlEmail(any(), any(), any(), any());
    }
}
