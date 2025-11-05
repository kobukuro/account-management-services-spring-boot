package com.peter.notificationservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("ci")
class NotificationServiceApplicationTests {

    @Configuration
    @EnableAutoConfiguration(exclude = MailSenderAutoConfiguration.class)
    static class TestConfig {
    }

    @Test
    void contextLoads() {
    }

}
