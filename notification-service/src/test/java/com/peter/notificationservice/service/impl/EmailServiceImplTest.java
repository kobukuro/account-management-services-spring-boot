package com.peter.notificationservice.service.impl;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmailServiceImpl.
 * Tests email sending functionality with HTML templates.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender emailSender;

    @Mock
    private TemplateEngine templateEngine;

    private EmailServiceImpl emailService;

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        emailService = new EmailServiceImpl(emailSender, templateEngine);

        // Set the @Value fields using reflection
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@example.com");
        ReflectionTestUtils.setField(emailService, "fromName", "Test App");

        // Create a real MimeMessage for testing
        Session session = Session.getInstance(System.getProperties());
        mimeMessage = new MimeMessage(session);
    }

    /**
     * Helper method to set up common mock behavior for email sending tests.
     * This reduces code duplication across multiple test methods.
     *
     * @param templateName the name of the template to process
     * @param processedHtml the HTML content to return from template processing
     */
    private void setupEmailMocks(String templateName, String processedHtml) {
        when(emailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq(templateName), any(Context.class))).thenReturn(processedHtml);
    }

    @Test
    void shouldSendHtmlEmailSuccessfully() throws Exception {
        // Given
        String toEmail = "recipient@example.com";
        String subject = "Test Subject";
        String templateName = "email/test-template";
        Map<String, Object> templateModel = Map.of(
                "firstName", "John",
                "lastName", "Doe"
        );
        String processedHtml = "<html><body>Hello John Doe</body></html>";

        setupEmailMocks(templateName, processedHtml);

        // When
        emailService.sendHtmlEmail(toEmail, subject, templateName, templateModel);

        // Then
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq(templateName), contextCaptor.capture());

        Context capturedContext = contextCaptor.getValue();
        assertEquals("John", capturedContext.getVariable("firstName"));
        assertEquals("Doe", capturedContext.getVariable("lastName"));

        verify(emailSender).createMimeMessage();
        verify(emailSender).send(mimeMessage);

        // Verify the MimeMessage properties
        assertEquals(toEmail, mimeMessage.getAllRecipients()[0].toString());
        assertEquals(subject, mimeMessage.getSubject());
        assertTrue(mimeMessage.getFrom()[0].toString().contains("noreply@example.com"));
    }

    @Test
    void shouldProcessTemplateWithCorrectVariables() {
        // Given
        String toEmail = "test@example.com";
        String subject = "Welcome Email";
        String templateName = "email/verification-email";
        Map<String, Object> templateModel = Map.of(
                "firstName", "Jane",
                "lastName", "Smith",
                "verificationLink", "http://example.com/verify",
                "appName", "My App",
                "expirationHours", 24
        );
        String processedHtml = "<html><body>Verification email content</body></html>";

        setupEmailMocks(templateName, processedHtml);

        // When
        emailService.sendHtmlEmail(toEmail, subject, templateName, templateModel);

        // Then
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq(templateName), contextCaptor.capture());

        Context capturedContext = contextCaptor.getValue();
        assertEquals("Jane", capturedContext.getVariable("firstName"));
        assertEquals("Smith", capturedContext.getVariable("lastName"));
        assertEquals("http://example.com/verify", capturedContext.getVariable("verificationLink"));
        assertEquals("My App", capturedContext.getVariable("appName"));
        assertEquals(24, capturedContext.getVariable("expirationHours"));
    }

    @Test
    void shouldThrowRuntimeExceptionWhenTemplateProcessingFails() {
        // Given
        String toEmail = "test@example.com";
        String subject = "Test Subject";
        String templateName = "email/invalid-template";
        Map<String, Object> templateModel = Map.of("key", "value");

        when(templateEngine.process(eq(templateName), any(Context.class)))
                .thenThrow(new RuntimeException("Template not found"));

        // When/Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> emailService.sendHtmlEmail(toEmail, subject, templateName, templateModel)
        );

        assertEquals("Failed to send HTML email", exception.getMessage());
        assertInstanceOf(RuntimeException.class, exception.getCause());
        assertEquals("Template not found", exception.getCause().getMessage());

        // Verify email was not sent
        verify(emailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void shouldThrowRuntimeExceptionWhenEmailSendingFails() {
        // Given
        String toEmail = "test@example.com";
        String subject = "Test Subject";
        String templateName = "email/test-template";
        Map<String, Object> templateModel = Map.of("key", "value");
        String processedHtml = "<html><body>Test content</body></html>";

        setupEmailMocks(templateName, processedHtml);
        doThrow(new RuntimeException("SMTP server connection failed"))
                .when(emailSender).send(any(MimeMessage.class));

        // When/Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> emailService.sendHtmlEmail(toEmail, subject, templateName, templateModel)
        );

        assertEquals("Failed to send HTML email", exception.getMessage());
        assertInstanceOf(RuntimeException.class, exception.getCause());
        assertEquals("SMTP server connection failed", exception.getCause().getMessage());
    }

    @Test
    void shouldHandleEmptyTemplateModel() {
        // Given
        String toEmail = "test@example.com";
        String subject = "Test Subject";
        String templateName = "email/simple-template";
        Map<String, Object> templateModel = Map.of();
        String processedHtml = "<html><body>Simple email</body></html>";

        setupEmailMocks(templateName, processedHtml);

        // When
        emailService.sendHtmlEmail(toEmail, subject, templateName, templateModel);

        // Then
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq(templateName), contextCaptor.capture());

        Context capturedContext = contextCaptor.getValue();
        assertTrue(capturedContext.getVariableNames().isEmpty());

        verify(emailSender).send(mimeMessage);
    }

    @Test
    void shouldSendEmailWithMultipleTemplateVariables() {
        // Given
        String toEmail = "user@example.com";
        String subject = "Password Reset";
        String templateName = "email/reset-password-email";
        Map<String, Object> templateModel = Map.of(
                "firstName", "Bob",
                "lastName", "Johnson",
                "appName", "Test Application",
                "resetPasswordLink", "http://example.com/reset?token=abc123",
                "expirationMinutes", 15
        );
        String processedHtml = "<html><body>Reset password email</body></html>";

        setupEmailMocks(templateName, processedHtml);

        // When
        emailService.sendHtmlEmail(toEmail, subject, templateName, templateModel);

        // Then
        verify(templateEngine).process(eq(templateName), any(Context.class));
        verify(emailSender).send(mimeMessage);
    }

    @Test
    void shouldUseCorrectFromEmailAndFromName() throws Exception {
        // Given
        String toEmail = "recipient@example.com";
        String subject = "Test";
        String templateName = "email/test";
        Map<String, Object> templateModel = Map.of();
        String processedHtml = "<html><body>Test</body></html>";

        setupEmailMocks(templateName, processedHtml);

        // When
        emailService.sendHtmlEmail(toEmail, subject, templateName, templateModel);

        // Then
        verify(emailSender).send(mimeMessage);

        // Verify the from address and name are set correctly
        assertNotNull(mimeMessage.getFrom());
        assertEquals(1, mimeMessage.getFrom().length);
        String fromAddress = mimeMessage.getFrom()[0].toString();
        assertTrue(fromAddress.contains("noreply@example.com"));
        assertTrue(fromAddress.contains("Test App"));
    }

    @Test
    void shouldHandleSpecialCharactersInSubject() throws Exception {
        // Given
        String toEmail = "test@example.com";
        String subject = "Welcome to Test App! 🎉";
        String templateName = "email/welcome";
        Map<String, Object> templateModel = Map.of("name", "User");
        String processedHtml = "<html><body>Welcome</body></html>";

        setupEmailMocks(templateName, processedHtml);

        // When
        emailService.sendHtmlEmail(toEmail, subject, templateName, templateModel);

        // Then
        verify(emailSender).send(mimeMessage);
        assertEquals(subject, mimeMessage.getSubject());
    }

    @Test
    void shouldSetUtf8Encoding() throws Exception {
        // Given
        String toEmail = "test@example.com";
        String subject = "Test Subject";
        String templateName = "email/test";
        Map<String, Object> templateModel = Map.of();
        String processedHtml = "<html><body>Test with UTF-8: 中文</body></html>";

        setupEmailMocks(templateName, processedHtml);

        // When
        emailService.sendHtmlEmail(toEmail, subject, templateName, templateModel);

        // Then
        verify(emailSender).send(mimeMessage);

        // Verify the HTML content was set
        assertNotNull(mimeMessage.getContent());
    }
}
