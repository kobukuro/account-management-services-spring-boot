package com.peter.authnservice.service.impl;

import com.peter.authnservice.service.EmailService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Map;

@Profile("ci")
@Service
public class MockEmailService implements EmailService {
    // Don't send email in CI environment
    @Override
    public void sendHtmlEmail(String to, String subject, String template, Map<String, Object> templateModel) {
    }
}

