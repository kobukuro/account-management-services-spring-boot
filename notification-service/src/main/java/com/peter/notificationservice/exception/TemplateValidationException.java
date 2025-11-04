package com.peter.notificationservice.exception;

import lombok.Getter;

import java.util.Set;

/**
 * Exception thrown when email template validation fails.
 * This occurs when required template variables are missing from the model.
 */
@Getter
public class TemplateValidationException extends RuntimeException {
    private final String templateName;
    private final Set<String> missingVariables;

    public TemplateValidationException(String templateName, Set<String> missingVariables) {
        super(String.format("Template validation failed for '%s'. Missing required variables: %s",
                templateName, missingVariables));
        this.templateName = templateName;
        this.missingVariables = missingVariables;
    }

}
