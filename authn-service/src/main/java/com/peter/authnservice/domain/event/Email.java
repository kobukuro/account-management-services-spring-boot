package com.peter.authnservice.domain.event;

import java.util.Map;

public record Email(
        String subject,
        String templateName,
        Map<String, Object> model) {
}
