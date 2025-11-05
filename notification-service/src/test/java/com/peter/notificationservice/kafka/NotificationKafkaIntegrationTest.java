package com.peter.notificationservice.kafka;

import com.peter.notificationservice.domain.event.Email;
import com.peter.notificationservice.domain.event.Event;
import com.peter.notificationservice.domain.event.UserDetails;
import com.peter.notificationservice.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Kafka consumer functionality in the notification service.
 */
@SpringBootTest
@ActiveProfiles("ci")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NotificationKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Event> kafkaTemplate;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        reset(emailService);
        doNothing().when(emailService).sendHtmlEmail(any(), any(), any(), any());
    }

    @Test
    void shouldConsumeUserRegistrationEvent() {
        // Given
        UserDetails userDetails = new UserDetails("John", "Doe", "john.doe@example.com");
        Email email = new Email(
                "Welcome to Our Service",
                "email/verification-email",
                Map.of("firstName", "John",
                        "lastName", "Doe",
                        "appName", "Our Service",
                        "verificationLink", "http://localhost:3000/activate?token=abc123",
                        "expirationHours", 24
                )
        );
        Event event = new Event(userDetails, email);

        // When
        kafkaTemplate.send("user_registration", event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(emailService, times(1)).sendHtmlEmail(
                        eq("john.doe@example.com"),
                        eq("Welcome to Our Service"),
                        eq("email/verification-email"),
                        eq(Map.of("firstName", "John",
                                "lastName", "Doe",
                                "appName", "Our Service",
                                "verificationLink", "http://localhost:3000/activate?token=abc123",
                                "expirationHours", 24
                        ))
                )
        );
    }

    @Test
    void shouldConsumePasswordResetEvent() {
        // Given
        UserDetails userDetails = new UserDetails("Jane", "Smith", "jane.smith@example.com");
        Email email = new Email(
                "Password Reset Request",
                "email/reset-password-email",
                Map.of("firstName", "Jane",
                        "lastName", "Smith",
                        "appName", "Our Service",
                        "resetPasswordLink", "http://localhost:3000/reset?token=xyz789",
                        "expirationMinutes", 30)
        );
        Event event = new Event(userDetails, email);

        // When
        kafkaTemplate.send("password_reset", event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(emailService, times(1)).sendHtmlEmail(
                        eq("jane.smith@example.com"),
                        eq("Password Reset Request"),
                        eq("email/reset-password-email"),
                        eq(Map.of("firstName", "Jane",
                                "lastName", "Smith",
                                "appName", "Our Service",
                                "resetPasswordLink", "http://localhost:3000/reset?token=xyz789",
                                "expirationMinutes", 30)
                        ))
        );
    }

    @Test
    void shouldConsumePasswordResetConfirmEvent() {
        // Given
        UserDetails userDetails = new UserDetails("Bob", "Johnson", "bob.johnson@example.com");
        Email email = new Email(
                "Password Reset Successful",
                "email/reset-password-confirm-email",
                Map.of("firstName", "Bob",
                        "lastName", "Johnson",
                        "appName", "Our Service")
        );
        Event event = new Event(userDetails, email);

        // When
        kafkaTemplate.send("password_reset_confirm", event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(emailService, times(1)).sendHtmlEmail(
                        eq("bob.johnson@example.com"),
                        eq("Password Reset Successful"),
                        eq("email/reset-password-confirm-email"),
                        eq(Map.of("firstName", "Bob",
                                "lastName", "Johnson",
                                "appName", "Our Service")
                        )
                )
        );
    }

    @Test
    void shouldConsumePasswordChangeEvent() {
        // Given
        UserDetails userDetails = new UserDetails("Alice", "Williams", "alice.williams@example.com");
        Email email = new Email(
                "Password Changed",
                "email/change-password-email",
                Map.of("firstName", "Alice",
                        "lastName", "Williams",
                        "appName", "Our Service")
        );
        Event event = new Event(userDetails, email);

        // When
        kafkaTemplate.send("password_change", event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(emailService, times(1)).sendHtmlEmail(
                        eq("alice.williams@example.com"),
                        eq("Password Changed"),
                        eq("email/change-password-email"),
                        eq(Map.of("firstName", "Alice",
                                "lastName", "Williams",
                                "appName", "Our Service"))
                )
        );
    }

    @Test
    void shouldConsumeResendActivationEvent() {
        // Given
        UserDetails userDetails = new UserDetails("Charlie", "Brown", "charlie.brown@example.com");
        Email email = new Email(
                "Resend Activation Link",
                "email/verification-email",
                Map.of("firstName", "John",
                        "lastName", "Doe",
                        "appName", "Our Service",
                        "verificationLink", "http://localhost:3000/activate?token=abc123",
                        "expirationHours", 24
                )
        );
        Event event = new Event(userDetails, email);

        // When
        kafkaTemplate.send("resend_activation", event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(emailService, times(1)).sendHtmlEmail(
                        eq("charlie.brown@example.com"),
                        eq("Resend Activation Link"),
                        eq("email/verification-email"),
                        eq(Map.of("firstName", "John",
                                "lastName", "Doe",
                                "appName", "Our Service",
                                "verificationLink", "http://localhost:3000/activate?token=abc123",
                                "expirationHours", 24
                        ))
                )
        );
    }

    @Test
    void shouldHandleMultipleEventsSequentially() {
        // Given - Multiple events for different topics
        UserDetails userDetails1 = new UserDetails("John", "Doe", "john.doe@example.com");
        Email email1 = new Email(
                "Welcome to Our Service",
                "email/verification-email",
                Map.of("firstName", "John",
                        "lastName", "Doe",
                        "appName", "Our Service",
                        "verificationLink", "http://localhost:3000/activate?token=abc123",
                        "expirationHours", 24
                )
        );
        Event event1 = new Event(userDetails1, email1);

        UserDetails userDetails2 = new UserDetails("Jane", "Smith", "jane.smith@example.com");
        Email email2 = new Email(
                "Password Reset Request",
                "email/reset-password-email",
                Map.of("firstName", "Jane",
                        "lastName", "Smith",
                        "appName", "Our Service",
                        "resetPasswordLink", "http://localhost:3000/reset?token=xyz789",
                        "expirationMinutes", 30)
        );
        Event event2 = new Event(userDetails2, email2);

        UserDetails userDetails3 = new UserDetails("Alice", "Williams", "alice.williams@example.com");
        Email email3 = new Email(
                "Password Changed",
                "email/change-password-email",
                Map.of("firstName", "Alice",
                        "lastName", "Williams",
                        "appName", "Our Service")
        );
        Event event3 = new Event(userDetails3, email3);

        // When - Send multiple events to different topics
        kafkaTemplate.send("user_registration", event1);
        kafkaTemplate.send("password_reset", event2);
        kafkaTemplate.send("password_change", event3);

        // Then - Verify all events were processed
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(emailService, times(3)).sendHtmlEmail(any(), any(), any(), any())
        );

        verify(emailService).sendHtmlEmail(
                eq("john.doe@example.com"),
                eq("Welcome to Our Service"),
                eq("email/verification-email"),
                eq(Map.of("firstName", "John",
                        "lastName", "Doe",
                        "appName", "Our Service",
                        "verificationLink", "http://localhost:3000/activate?token=abc123",
                        "expirationHours", 24
                ))
        );

        verify(emailService).sendHtmlEmail(
                eq("jane.smith@example.com"),
                eq("Password Reset Request"),
                eq("email/reset-password-email"),
                eq(Map.of("firstName", "Jane",
                        "lastName", "Smith",
                        "appName", "Our Service",
                        "resetPasswordLink", "http://localhost:3000/reset?token=xyz789",
                        "expirationMinutes", 30)
                )
        );

        verify(emailService).sendHtmlEmail(
                eq("alice.williams@example.com"),
                eq("Password Changed"),
                eq("email/change-password-email"),
                eq(Map.of("firstName", "Alice",
                        "lastName", "Williams",
                        "appName", "Our Service"))
        );
    }
}
