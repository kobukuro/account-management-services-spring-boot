package com.peter.authnservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peter.authnservice.domain.dto.ActivationEmailResendRequest;
import com.peter.authnservice.domain.dto.UserActivationRequest;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
public class UserRegistrationIntegrationTest {

    private static final String REGISTER_API_PATH = "/api/v1/users";
    private static final String ACTIVATION_API_PATH = "/api/v1/users/activation";
    private static final String RESEND_ACTIVATION_API_PATH = "/api/v1/users/resend-activation";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private Flyway flyway;

    private static Consumer<String, Event> consumer;

    private final String firstName = "John";
    private final String lastName = "Doe";
    private final String testEmail = "test@example.com";
    private final String password = "Password123!";

    private final UserRegistrationRequest validRequest = new UserRegistrationRequest(firstName, lastName, testEmail, password);

    @Value("${app-name}")
    private String appName;

    @Value("${jwt.verification.expiration}")
    private long verificationTokenExpirationInMilliseconds;

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
        consumer.subscribe(Arrays.asList("user_registration", "resend_activation"));
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
     * Test normal registration flow
     */
    @Test
    void whenValidInput_thenReturns201AndSendsKafkaMessage() throws Exception {
        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(validRequest.email()));

        assertTrue(userRepository.findByEmail(validRequest.email()).isPresent());

        ConsumerRecords<String, Event> records =
                consumer.poll(Duration.ofSeconds(5));
        assertFalse(records.isEmpty());

        ConsumerRecord<String, Event> record = records.iterator().next();
        Event event = record.value();
        assertEquals(testEmail, event.userDetails().email());
        assertEquals(firstName, event.userDetails().firstName());
        assertEquals(lastName, event.userDetails().lastName());
        assertEquals("Account activation on " + appName,
                event.email().subject());
        assertEquals("email/verification-email", event.email().templateName());
        assertEquals(appName, event.email().model().get("appName"));
        assertEquals(firstName, event.email().model().get("firstName"));
        assertEquals(lastName, event.email().model().get("lastName"));
        Long expectedHours = verificationTokenExpirationInMilliseconds / 3600000L;
        Long actualHours = Long.valueOf(event.email().model().get("expirationHours").toString());
        assertEquals(expectedHours, actualHours);
    }

    /**
     * Test password without uppercase letter
     */
    @Test
    void whenPasswordWithoutUppercase_thenReturns400() throws Exception {
        UserRegistrationRequest invalidRequest = new UserRegistrationRequest(
                firstName,
                lastName,
                testEmail,
                "password123!"
        );

        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        assertEquals(0, userRepository.count());
    }

    /**
     * Test password without lowercase letter
     */
    @Test
    void whenPasswordWithoutLowercase_thenReturns400() throws Exception {
        UserRegistrationRequest invalidRequest = new UserRegistrationRequest(
                firstName,
                lastName,
                testEmail,
                "PASSWORD123!"
        );

        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        assertEquals(0, userRepository.count());
    }

    /**
     * Test password without number
     */
    @Test
    void whenPasswordWithoutNumber_thenReturns400() throws Exception {
        UserRegistrationRequest invalidRequest = new UserRegistrationRequest(
                firstName,
                lastName,
                testEmail,
                "Password!@#"
        );

        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        assertEquals(0, userRepository.count());
    }

    /**
     * Test password without special character
     */
    @Test
    void whenPasswordWithoutSpecialChar_thenReturns400() throws Exception {
        UserRegistrationRequest invalidRequest = new UserRegistrationRequest(
                firstName,
                lastName,
                testEmail,
                "Password123"
        );

        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        assertEquals(0, userRepository.count());
    }

    /**
     * Test password too short (less than 8 characters)
     */
    @Test
    void whenPasswordTooShort_thenReturns400() throws Exception {
        UserRegistrationRequest invalidRequest = new UserRegistrationRequest(
                firstName,
                lastName,
                testEmail,
                "Pass1!"
        );

        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        assertEquals(0, userRepository.count());
    }

    /**
     * Test password with whitespace
     */
    @Test
    void whenPasswordWithWhitespace_thenReturns400() throws Exception {
        UserRegistrationRequest invalidRequest = new UserRegistrationRequest(
                firstName,
                lastName,
                testEmail,
                "Password 123!"
        );

        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        assertEquals(0, userRepository.count());
    }

    /**
     * Test empty first name
     */
    @Test
    void whenEmptyFirstName_thenReturns400() throws Exception {
        UserRegistrationRequest invalidRequest = new UserRegistrationRequest(
                "",
                lastName,
                testEmail,
                password
        );

        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        assertEquals(0, userRepository.count());
    }

    /**
     * Test empty last name
     */
    @Test
    void whenEmptyLastName_thenReturns400() throws Exception {
        UserRegistrationRequest invalidRequest = new UserRegistrationRequest(
                firstName,
                "",
                testEmail,
                password
        );

        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        assertEquals(0, userRepository.count());
    }

    /**
     * Test invalid email format
     */
    @Test
    void whenInvalidEmail_thenReturns400() throws Exception {
        UserRegistrationRequest invalidRequest = new UserRegistrationRequest(
                firstName,
                lastName,
                "invalid-email",
                password
        );

        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        assertFalse(userRepository.findByEmail(invalidRequest.email()).isPresent());
    }

    /**
     * Test duplicate registration
     */
    @Test
    void whenDuplicateEmail_thenReturns409() throws Exception {
        // First registration
        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        // Attempt to register with the same email again
        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isConflict());

        assertEquals(1, userRepository.count());
    }

    /**
     * Test empty email
     */
    @Test
    void whenEmptyEmail_thenReturns400() throws Exception {
        UserRegistrationRequest invalidRequest = new UserRegistrationRequest(
                firstName,
                lastName,
                "",
                password
        );

        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        assertEquals(0, userRepository.count());
    }

    /**
     * Test empty password
     */
    @Test
    void whenEmptyPassword_thenReturns400() throws Exception {
        UserRegistrationRequest invalidRequest = new UserRegistrationRequest(
                firstName,
                lastName,
                "test@example.com",
                ""
        );

        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        assertEquals(0, userRepository.count());
    }

    /**
     * Test successful account activation
     */
    @Test
    void whenValidToken_thenActivateAccount() throws Exception {
        // First register a user
        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        String validToken = jwtUtils.generateVerificationToken(testEmail);
        UserActivationRequest activationRequest = new UserActivationRequest(validToken);

        mockMvc.perform(post(ACTIVATION_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activationRequest)))
                .andExpect(status().isNoContent());

        AppUser user = userRepository.findByEmail(testEmail).orElseThrow();
        assertTrue(user.getEnabled());
    }

    /**
     * Test activation with invalid token
     */
    @Test
    void whenInvalidToken_thenReturns401() throws Exception {
        // Register a user first
        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        UserActivationRequest invalidRequest = new UserActivationRequest("invalid-token");

        mockMvc.perform(post(ACTIVATION_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isUnauthorized());

        AppUser user = userRepository.findByEmail(testEmail).orElseThrow();
        assertFalse(user.getEnabled());
    }

    /**
     * Test activation of already activated account
     */
    @Test
    void whenAlreadyActivated_thenReturns409() throws Exception {
        // Register and activate user
        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        String validToken = jwtUtils.generateVerificationToken(testEmail);
        UserActivationRequest activationRequest = new UserActivationRequest(validToken);

        // First activation
        mockMvc.perform(post(ACTIVATION_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activationRequest)))
                .andExpect(status().isNoContent());

        // Try to activate again
        mockMvc.perform(post(ACTIVATION_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activationRequest)))
                .andExpect(status().isConflict());
    }

    /**
     * Test activation with token containing non-existent email
     */
    @Test
    void whenNonExistentEmail_thenReturns404() throws Exception {
        String tokenWithNonExistentEmail = jwtUtils.generateVerificationToken("nonexistent@example.com");
        UserActivationRequest activationRequest = new UserActivationRequest(tokenWithNonExistentEmail);

        mockMvc.perform(post(ACTIVATION_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activationRequest)))
                .andExpect(status().isNotFound());
    }

    /**
     * Test successful resend activation email for unverified user
     */
    @Test
    void whenResendActivationForUnverifiedUser_thenReturns204AndSendsKafkaMessage() throws Exception {
        // First register a user (but don't activate)
        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        // Clear registration messages
        consumer.poll(Duration.ofSeconds(5));

        ActivationEmailResendRequest resendRequest = new ActivationEmailResendRequest(testEmail);

        // Resend activation email
        mockMvc.perform(post(RESEND_ACTIVATION_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendRequest)))
                .andExpect(status().isNoContent());

        // Verify Kafka message was sent
        ConsumerRecords<String, Event> records =
                consumer.poll(Duration.ofSeconds(5));
        assertFalse(records.isEmpty());

        ConsumerRecord<String, Event> record = records.iterator().next();
        Event event = record.value();
        assertEquals(testEmail, event.userDetails().email());
        assertEquals(firstName, event.userDetails().firstName());
        assertEquals(lastName, event.userDetails().lastName());
        assertEquals("Account activation on " + appName, event.email().subject());
        assertEquals("email/verification-email", event.email().templateName());
        assertEquals(appName, event.email().model().get("appName"));
        assertEquals(firstName, event.email().model().get("firstName"));
        assertEquals(lastName, event.email().model().get("lastName"));

        Long expectedHours = verificationTokenExpirationInMilliseconds / 3600000L;
        Long actualHours = Long.valueOf(event.email().model().get("expirationHours").toString());
        assertEquals(expectedHours, actualHours);

        // Verify verification link contains a token
        String verificationLink = (String) event.email().model().get("verificationLink");
        assertTrue(verificationLink.contains("token="));
    }

    /**
     * Test resend activation email for non-existent email
     */
    @Test
    void whenResendActivationForNonExistentEmail_thenReturns404() throws Exception {
        ActivationEmailResendRequest resendRequest = new ActivationEmailResendRequest("nonexistent@example.com");

        mockMvc.perform(post(RESEND_ACTIVATION_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendRequest)))
                .andExpect(status().isNotFound());
    }

    /**
     * Test resend activation email for already verified user
     */
    @Test
    void whenResendActivationForVerifiedUser_thenReturns409() throws Exception {
        // Register and activate user
        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        String validToken = jwtUtils.generateVerificationToken(testEmail);
        UserActivationRequest activationRequest = new UserActivationRequest(validToken);

        mockMvc.perform(post(ACTIVATION_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activationRequest)))
                .andExpect(status().isNoContent());

        // Clear any pending messages
        consumer.poll(Duration.ofSeconds(5));

        // Try to resend activation email for already verified user
        ActivationEmailResendRequest resendRequest = new ActivationEmailResendRequest(testEmail);

        mockMvc.perform(post(RESEND_ACTIVATION_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendRequest)))
                .andExpect(status().isConflict());
    }

    /**
     * Test resend activation email with invalid email format
     */
    @Test
    void whenResendActivationWithInvalidEmail_thenReturns400() throws Exception {
        ActivationEmailResendRequest invalidRequest = new ActivationEmailResendRequest("invalid-email");

        mockMvc.perform(post(RESEND_ACTIVATION_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test resend activation email with empty email
     */
    @Test
    void whenResendActivationWithEmptyEmail_thenReturns400() throws Exception {
        ActivationEmailResendRequest emptyEmailRequest = new ActivationEmailResendRequest("");

        mockMvc.perform(post(RESEND_ACTIVATION_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyEmailRequest)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test resend activation email with null email
     */
    @Test
    void whenResendActivationWithNullEmail_thenReturns400() throws Exception {
        String requestBody = "{\"email\": null}";

        mockMvc.perform(post(RESEND_ACTIVATION_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }
}