package com.peter.authnservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peter.authnservice.domain.dto.PasswordResetConfirmRequest;
import com.peter.authnservice.domain.dto.PasswordResetRequest;
import com.peter.authnservice.domain.dto.UserRegistrationRequest;
import com.peter.authnservice.domain.entity.AppUser;
import com.peter.authnservice.domain.event.Event;
import com.peter.authnservice.repository.UserRepository;
import com.peter.authnservice.util.JwtUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
public class UserResetPasswordIntegrationTest {

    private static final String RESET_PASSWORD_API_PATH = "/api/v1/users/reset-password";
    private static final String RESET_PASSWORD_CONFIRM_API_PATH = "/api/v1/users/reset-password-confirm";
    private static final String REGISTER_API_PATH = "/api/v1/users";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Flyway flyway;

    private static Consumer<String, Event> consumer;

    private final String firstName = "Jane";
    private final String lastName = "Smith";
    private final String testEmail = "test@example.com";
    private final String password = "Password123!";

    private final UserRegistrationRequest validRequest = new UserRegistrationRequest(firstName, lastName, testEmail, password);

    @Value("${app-name}")
    private String appName;

    @Value("${jwt.reset-password.expiration}")
    private long resetPasswordTokenExpirationInMilliseconds;

    @BeforeAll
    static void setupKafkaConsumer() {
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        ConsumerFactory<String, Event> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps,
                        new StringDeserializer(),
                        new JsonDeserializer<>(Event.class, false));

        consumer = consumerFactory.createConsumer();
        consumer.subscribe(Collections.singletonList("password_reset"));
        consumer.subscribe(Collections.singletonList("password_reset_confirm"));
    }

    @BeforeEach
    void setUp() {
        // Reset database before each test
        flyway.clean();
        flyway.migrate();

        consumer.poll(Duration.ofMillis(100));
    }

    @AfterEach
    void cleanup() {
        userRepository.deleteAll();
    }

    @AfterAll
    static void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    /**
     * Test successful password reset request
     */
    @Test
    void whenValidEmail_thenReturns204AndSendsKafkaMessage() throws Exception {
        // Register a user first
        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        AppUser user = userRepository.findByEmail(testEmail).orElseThrow();
        user.setEnabled(true);
        userRepository.save(user);

        PasswordResetRequest resetRequest = new PasswordResetRequest(testEmail);

        mockMvc.perform(post(RESET_PASSWORD_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isNoContent());


        // Check if the password reset event was sent to Kafka
        ConsumerRecords<String, Event> records = consumer.poll(Duration.ofSeconds(5));
        assertFalse(records.isEmpty());

        ConsumerRecord<String, Event> record = records.iterator().next();
        Event event = record.value();
        assertEquals(testEmail, event.userDetails().email());
        assertEquals("Reset password on " + appName, event.email().subject());
        assertEquals("email/reset-password-email", event.email().templateName());
        assertEquals(appName, event.email().model().get("appName"));
        assertEquals(firstName, event.email().model().get("firstName"));
        assertEquals(lastName, event.email().model().get("lastName"));
        Long expectedMinutes = resetPasswordTokenExpirationInMilliseconds / 60000L;
        Long actualMinutes = Long.valueOf(event.email().model().get("expirationMinutes").toString());
        assertEquals(expectedMinutes, actualMinutes);
    }

    /**
     * Test password reset for unverified email
     */
    @Test
    void whenEmailNotVerified_thenReturns403() throws Exception {
        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        PasswordResetRequest resetRequest = new PasswordResetRequest(testEmail);

        mockMvc.perform(post(RESET_PASSWORD_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isForbidden());
    }

    /**
     * Test password reset for non-existent email
     */
    @Test
    void whenNonExistentEmail_thenReturns404() throws Exception {
        PasswordResetRequest resetRequest = new PasswordResetRequest("nonexistent@example.com");

        mockMvc.perform(post(RESET_PASSWORD_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isNotFound());
    }

    /**
     * Test password reset request with empty email
     */
    @Test
    void whenEmptyEmail_thenReturns400() throws Exception {
        PasswordResetRequest resetRequest = new PasswordResetRequest("");

        mockMvc.perform(post(RESET_PASSWORD_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test successful password reset confirmation
     */
    @Test
    void whenValidResetPasswordConfirm_thenReturns204AndUpdatesPassword() throws Exception {
        // Register and enable user
        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        AppUser user = userRepository.findByEmail(testEmail).orElseThrow();
        user.setEnabled(true);
        userRepository.save(user);

        String resetToken = jwtUtils.generateResetPasswordToken(testEmail);
        String newPassword = "NewPassword123!";
        PasswordResetConfirmRequest confirmRequest = new PasswordResetConfirmRequest(
                resetToken, newPassword);

        // Perform password reset confirmation
        mockMvc.perform(post(RESET_PASSWORD_CONFIRM_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isNoContent());

        // Verify password is updated
        AppUser updatedUser = userRepository.findByEmail(testEmail).orElseThrow();
        assertTrue(BCrypt.checkpw(newPassword, updatedUser.getPassword()));

        // Verify Kafka event
        ConsumerRecords<String, Event> records = consumer.poll(Duration.ofSeconds(5));
        assertFalse(records.isEmpty());

        ConsumerRecord<String, Event> record = records.iterator().next();
        Event event = record.value();
        assertEquals(testEmail, event.userDetails().email());
        assertEquals("Password reset confirmation on " + appName, event.email().subject());
        assertEquals("email/reset-password-confirm-email", event.email().templateName());
    }

    /**
     * Test password reset confirmation with invalid token
     */
    @Test
    void whenInvalidResetToken_thenReturns401() throws Exception {
        PasswordResetConfirmRequest confirmRequest = new PasswordResetConfirmRequest(
                "invalid-token", "NewPassword123!");

        mockMvc.perform(post(RESET_PASSWORD_CONFIRM_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test password reset confirmation for unverified email
     */
    @Test
    void whenResetPasswordConfirmWithUnverifiedEmail_thenReturns403() throws Exception {
        // Register user but don't enable
        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        String resetToken = jwtUtils.generateResetPasswordToken(testEmail);
        PasswordResetConfirmRequest confirmRequest = new PasswordResetConfirmRequest(
                resetToken, "NewPassword123!");

        mockMvc.perform(post(RESET_PASSWORD_CONFIRM_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isForbidden());
    }

    /**
     * Test password reset confirmation with empty password
     */
    @Test
    void whenEmptyPasswordInConfirmation_thenReturns400() throws Exception {
        String resetToken = jwtUtils.generateResetPasswordToken(testEmail);
        PasswordResetConfirmRequest confirmRequest = new PasswordResetConfirmRequest(
                resetToken, "");

        mockMvc.perform(post(RESET_PASSWORD_CONFIRM_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isBadRequest());
    }
}
