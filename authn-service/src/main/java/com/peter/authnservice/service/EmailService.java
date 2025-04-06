package com.peter.authnservice.service;

import java.util.Map;

public interface EmailService {
    void sendHtmlEmail(String toEmail, String subject, String templateName, Map<String, Object> templateModel);
}
