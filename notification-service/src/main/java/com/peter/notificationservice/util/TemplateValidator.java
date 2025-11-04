package com.peter.notificationservice.util;

import com.peter.notificationservice.exception.TemplateValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates that email template models contain all required variables.
 * Each template has a defined set of required variables that must be present.
 */
@Slf4j
@Component
public class TemplateValidator {

    // Define required variables for each template
    private static final Map<String, Set<String>> TEMPLATE_REQUIRED_VARIABLES = Map.of(
            "email/verification-email", Set.of("firstName", "lastName", "appName", "verificationLink", "expirationHours"),
            "email/reset-password-email", Set.of("firstName", "lastName", "appName", "resetPasswordLink", "expirationMinutes"),
            "email/reset-password-confirm-email", Set.of("firstName", "lastName", "appName"),
            "email/change-password-email", Set.of("firstName", "lastName", "appName")
    );

    /**
     * Validates that the template model contains all required variables.
     *
     * @param templateName the name of the template to validate
     * @param model the template model containing variables
     * @throws TemplateValidationException if required variables are missing
     */
    public void validate(String templateName, Map<String, Object> model) {
        Set<String> requiredVariables = TEMPLATE_REQUIRED_VARIABLES.get(templateName);

        // If template is not registered, log warning but don't fail
        // This allows for flexible template usage
        if (requiredVariables == null) {
            log.warn("No validation rules defined for template: {}. Skipping validation.", templateName);
            return;
        }

        // Check for missing variables
        Set<String> missingVariables = requiredVariables.stream()
                .filter(var -> !model.containsKey(var) || model.get(var) == null)
                .collect(Collectors.toSet());

        if (!missingVariables.isEmpty()) {
            log.error("Template validation failed for '{}'. Missing variables: {}", templateName, missingVariables);
            throw new TemplateValidationException(templateName, missingVariables);
        }

        log.debug("Template validation passed for '{}'. All required variables present.", templateName);
    }

    /**
     * Gets the required variables for a specific template.
     *
     * @param templateName the name of the template
     * @return set of required variable names, or empty set if template not registered
     */
    public Set<String> getRequiredVariables(String templateName) {
        return TEMPLATE_REQUIRED_VARIABLES.getOrDefault(templateName, Collections.emptySet());
    }

    /**
     * Checks if a template has validation rules defined.
     *
     * @param templateName the name of the template
     * @return true if validation rules are defined, false otherwise
     */
    public boolean hasValidationRules(String templateName) {
        return TEMPLATE_REQUIRED_VARIABLES.containsKey(templateName);
    }
}
