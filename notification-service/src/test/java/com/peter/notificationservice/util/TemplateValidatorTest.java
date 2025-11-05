package com.peter.notificationservice.util;

import com.peter.notificationservice.exception.TemplateValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TemplateValidator.
 */
class TemplateValidatorTest {

    private TemplateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TemplateValidator();
    }

    @Test
    void shouldPassValidationForVerificationEmailWithAllRequiredVariables() {
        // Given
        Map<String, Object> model = Map.of(
                "firstName", "John",
                "lastName", "Doe",
                "appName", "My App",
                "verificationLink", "http://example.com/verify",
                "expirationHours", 24
        );

        // When/Then - should not throw exception
        assertDoesNotThrow(() -> validator.validate("email/verification-email", model));
    }

    @Test
    void shouldThrowExceptionWhenVerificationEmailMissingRequiredVariable() {
        // Given - missing verificationLink
        Map<String, Object> model = Map.of(
                "firstName", "John",
                "lastName", "Doe",
                "appName", "My App",
                "expirationHours", 24
        );

        // When/Then
        TemplateValidationException exception = assertThrows(
                TemplateValidationException.class,
                () -> validator.validate("email/verification-email", model)
        );

        assertEquals("email/verification-email", exception.getTemplateName());
        assertTrue(exception.getMissingVariables().contains("verificationLink"));
        assertTrue(exception.getMessage().contains("verificationLink"));
    }

    @Test
    void shouldThrowExceptionWhenMultipleVariablesAreMissing() {
        // Given - missing multiple variables
        Map<String, Object> model = Map.of(
                "firstName", "John"
        );

        // When/Then
        TemplateValidationException exception = assertThrows(
                TemplateValidationException.class,
                () -> validator.validate("email/verification-email", model)
        );

        Set<String> missingVars = exception.getMissingVariables();
        assertTrue(missingVars.contains("lastName"));
        assertTrue(missingVars.contains("appName"));
        assertTrue(missingVars.contains("verificationLink"));
        assertTrue(missingVars.contains("expirationHours"));
    }

    @Test
    void shouldPassValidationForResetPasswordEmailWithAllRequiredVariables() {
        // Given
        Map<String, Object> model = Map.of(
                "firstName", "Jane",
                "lastName", "Smith",
                "appName", "My App",
                "resetPasswordLink", "http://example.com/reset",
                "expirationMinutes", 15
        );

        // When/Then
        assertDoesNotThrow(() -> validator.validate("email/reset-password-email", model));
    }

    @Test
    void shouldPassValidationForResetPasswordConfirmEmailWithAllRequiredVariables() {
        // Given
        Map<String, Object> model = Map.of(
                "firstName", "Bob",
                "lastName", "Johnson",
                "appName", "My App"
        );

        // When/Then
        assertDoesNotThrow(() -> validator.validate("email/reset-password-confirm-email", model));
    }

    @Test
    void shouldPassValidationForChangePasswordEmailWithAllRequiredVariables() {
        // Given
        Map<String, Object> model = Map.of(
                "firstName", "Alice",
                "lastName", "Williams",
                "appName", "My App"
        );

        // When/Then
        assertDoesNotThrow(() -> validator.validate("email/change-password-email", model));
    }

    @Test
    void shouldThrowExceptionWhenVariableIsNull() {
        // Given - firstName is null
        Map<String, Object> model = new HashMap<>();
        model.put("firstName", null);
        model.put("lastName", "Doe");
        model.put("appName", "My App");

        // When/Then
        TemplateValidationException exception = assertThrows(
                TemplateValidationException.class,
                () -> validator.validate("email/change-password-email", model)
        );

        assertTrue(exception.getMissingVariables().contains("firstName"));
    }

    @Test
    void shouldNotThrowExceptionForUnknownTemplate() {
        // Given
        Map<String, Object> model = Map.of("anyKey", "anyValue");

        assertDoesNotThrow(() -> validator.validate("unknown/template", model));
    }

    @Test
    void shouldAllowExtraVariablesInModel() {
        // Given - model has extra variables not required by template
        Map<String, Object> model = Map.of(
                "firstName", "John",
                "lastName", "Doe",
                "appName", "My App",
                "extraVariable1", "value1",
                "extraVariable2", "value2"
        );

        // When/Then - should pass validation
        assertDoesNotThrow(() -> validator.validate("email/change-password-email", model));
    }

    @Test
    void shouldReturnRequiredVariablesForTemplate() {
        // When
        Set<String> requiredVars = validator.getRequiredVariables("email/verification-email");

        // Then
        assertEquals(5, requiredVars.size());
        assertTrue(requiredVars.contains("firstName"));
        assertTrue(requiredVars.contains("lastName"));
        assertTrue(requiredVars.contains("appName"));
        assertTrue(requiredVars.contains("verificationLink"));
        assertTrue(requiredVars.contains("expirationHours"));
    }

    @Test
    void shouldReturnEmptySetForUnknownTemplate() {
        // When
        Set<String> requiredVars = validator.getRequiredVariables("unknown/template");

        // Then
        assertTrue(requiredVars.isEmpty());
    }

    @Test
    void shouldReturnTrueForTemplateWithValidationRules() {
        // When/Then
        assertTrue(validator.hasValidationRules("email/verification-email"));
        assertTrue(validator.hasValidationRules("email/reset-password-email"));
        assertTrue(validator.hasValidationRules("email/reset-password-confirm-email"));
        assertTrue(validator.hasValidationRules("email/change-password-email"));
    }

    @Test
    void shouldReturnFalseForTemplateWithoutValidationRules() {
        // When/Then
        assertFalse(validator.hasValidationRules("unknown/template"));
    }
}
