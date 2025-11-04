package com.peter.authnservice;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peter.authnservice.domain.dto.PasswordChangeRequest;
import com.peter.authnservice.domain.entity.AppUser;
import com.peter.authnservice.domain.entity.AuthenticationType;
import com.peter.authnservice.domain.entity.UserAuthentication;
import com.peter.authnservice.domain.event.Event;
import com.peter.authnservice.repository.UserAuthenticationRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.*;

import static com.peter.authnservice.domain.entity.UserAuthentication.createLocalAuth;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
public class PasswordChangeIntegrationTest {

    private static final String CHANGE_PASSWORD_API_PATH = "/api/v1/users/change-password";
    private static final String KAFKA_TOPIC = "password_change";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthenticationRepository userAuthenticationRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private Flyway flyway;

    private static Consumer<String, Event> consumer;

    private final String firstName = "John";
    private final String lastName = "Doe";
    private final String testEmail = "test@example.com";
    private final String currentPassword = "Password123! ";
    private final String newPassword = "NewPassword456! ";

    @Value("${app-name}")
    private String appName;

    @Value("${test.kafka.max-poll-iterations}")
    private int maxKafkaPollIterations;

    @Value("${secret-key}")
    private String secretKey;

    @BeforeAll
    static void setupKafkaConsumer() {
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-password-change");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        ConsumerFactory<String, Event> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps,
                        new StringDeserializer(),
                        new JsonDeserializer<>(Event.class, false));

        consumer = consumerFactory.createConsumer();
        consumer.subscribe(Collections.singletonList(KAFKA_TOPIC));
    }

    @BeforeEach
    void setUp() {
        // Reset database before each test
        flyway.clean();
        flyway.migrate();

        // Clear Kafka events before each test
        ConsumerRecords<String, Event> records;
        int iteration = 0;
        do {
            records = consumer.poll(Duration.ofMillis(500));
            iteration++;
        } while (!records.isEmpty() && iteration < maxKafkaPollIterations);
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
     * Helper method to create and save an activated user
     */
    private AppUser createActivatedUser() {
        String hashedPassword = BCrypt.hashpw(currentPassword, BCrypt.gensalt());

        AppUser user = new AppUser(firstName, lastName, true);
        UserAuthentication userAuth = createLocalAuth(user, testEmail, hashedPassword);
        AppUser appUser = userRepository.save(user);
        userAuthenticationRepository.save(userAuth);

        return appUser;
    }

    /**
     * Helper method to generate access token for user
     */
    private String generateAccessToken(UUID userId) {
        return jwtUtils.generateAccessToken(userId);
    }

    /**
     * Test successful password change
     */
    @Test
    void whenValidPasswordChange_thenReturns204AndSendsKafkaMessage() throws Exception {
        AppUser user = createActivatedUser();
        String accessToken = generateAccessToken(user.getId());

        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, newPassword);

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        // Verify password was changed in database
        UserAuthentication userAuth = userAuthenticationRepository.findByUserIdAndType(user.getId(), AuthenticationType.LOCAL).orElseThrow();
        assertTrue(BCrypt.checkpw(newPassword, userAuth.getPassword()));
        assertFalse(BCrypt.checkpw(currentPassword, userAuth.getPassword()));

        // Verify Kafka message was sent
        ConsumerRecords<String, Event> records = consumer.poll(Duration.ofSeconds(5));
        assertFalse(records.isEmpty());

        ConsumerRecord<String, Event> record = records.iterator().next();
        Event event = record.value();
        assertEquals(testEmail, event.userDetails().email());
        assertEquals(firstName, event.userDetails().firstName());
        assertEquals(lastName, event.userDetails().lastName());
        assertEquals("Password changed on " + appName, event.email().subject());
        assertEquals("email/change-password-email", event.email().templateName());
        assertEquals(appName, event.email().model().get("appName"));
        assertEquals(firstName, event.email().model().get("firstName"));
        assertEquals(lastName, event.email().model().get("lastName"));
    }

    /**
     * Test password change with incorrect current password
     */
    @Test
    void whenIncorrectCurrentPassword_thenReturns401() throws Exception {
        AppUser user = createActivatedUser();
        String accessToken = generateAccessToken(user.getId());

        PasswordChangeRequest request = new PasswordChangeRequest("WrongPassword123!", newPassword);

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        // Verify password was not changed
        UserAuthentication userAuth = userAuthenticationRepository.findByUserIdAndType(user.getId(), AuthenticationType.LOCAL).orElseThrow();
        assertTrue(BCrypt.checkpw(currentPassword, userAuth.getPassword()));
        assertFalse(BCrypt.checkpw(newPassword, userAuth.getPassword()));
    }

    /**
     * Test password change without authentication token
     */
    @Test
    void whenNoAuthToken_thenReturns401() throws Exception {
        createActivatedUser();

        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, newPassword);

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test password change with invalid authentication token
     */
    @Test
    void whenInvalidAuthToken_thenReturns401() throws Exception {
        createActivatedUser();

        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, newPassword);

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test with token signed with wrong secret
     */
    @Test
    void whenTokenWithWrongSecret_thenReturns401() throws Exception {
        AppUser user = createActivatedUser();

        // Create token with wrong secret
        Algorithm wrongAlgorithm = Algorithm.HMAC256("wrong-secret-key");
        String tokenWithWrongSignature = JWT.create()
                .withSubject(user.getId().toString())
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000))
                .sign(wrongAlgorithm);

        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, newPassword);

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithWrongSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test password change with expired access token
     */
    @Test
    void whenExpiredAccessToken_thenReturns401() throws Exception {
        AppUser user = createActivatedUser();

        // Create expired token (expired 1 hour ago)
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String expiredToken = JWT.create()
                .withSubject(user.getId().toString())
                .withIssuedAt(new Date(System.currentTimeMillis() - 7200000)) // 2 hours ago
                .withExpiresAt(new Date(System.currentTimeMillis() - 3600000)) // 1 hour ago
                .sign(algorithm);

        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, newPassword);

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test password change with non-existent user ID in token
     */
    @Test
    void whenNonExistentUserId_thenReturns401() throws Exception {
        createActivatedUser();
        String accessToken = generateAccessToken(UUID.randomUUID()); // Generate token for non-existent user

        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, newPassword);

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test with token containing invalid user ID format
     */
    @Test
    void whenTokenWithInvalidUserIdFormat_thenReturns401() throws Exception {
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String tokenWithInvalidUserId = JWT.create()
                .withSubject("not-a-valid-uuid")
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000))
                .sign(algorithm);

        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, newPassword);

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithInvalidUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test with missing Authorization header on protected endpoint
     */
    @Test
    void whenMissingAuthorizationHeader_thenReturns401() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, newPassword);

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test with empty Authorization header
     */
    @Test
    void whenEmptyAuthorizationHeader_thenReturns401() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, newPassword);

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test with Authorization header without Bearer prefix
     */
    @Test
    void whenAuthorizationHeaderWithoutBearer_thenReturns401() throws Exception {
        AppUser user = createActivatedUser();
        String token = jwtUtils.generateAccessToken(user.getId());

        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, newPassword);

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, token) // Missing "Bearer " prefix
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test with Bearer prefix but no token
     */
    @Test
    void whenBearerPrefixWithoutToken_thenReturns401() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, newPassword);

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test with lowercase bearer prefix
     */
    @Test
    void whenLowercaseBearerPrefix_thenReturns401() throws Exception {
        AppUser user = createActivatedUser();
        String token = jwtUtils.generateAccessToken(user.getId());

        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, newPassword);

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test password change with invalid new password format (no uppercase)
     */
    @Test
    void whenInvalidNewPasswordFormat_thenReturns400() throws Exception {
        AppUser user = createActivatedUser();
        String accessToken = generateAccessToken(user.getId());

        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, "newpassword123!");

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // Verify password was not changed
        UserAuthentication unchangedUserAuth = userAuthenticationRepository.findByUserIdAndType(user.getId(), AuthenticationType.LOCAL).orElseThrow();
        assertTrue(BCrypt.checkpw(currentPassword, unchangedUserAuth.getPassword()));
    }

    /**
     * Test password change with invalid new password format (no lowercase)
     */
    @Test
    void whenNewPasswordWithoutLowercase_thenReturns400() throws Exception {
        AppUser user = createActivatedUser();
        String accessToken = generateAccessToken(user.getId());

        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, "NEWPASSWORD123!");

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test password change with invalid new password format (no number)
     */
    @Test
    void whenNewPasswordWithoutNumber_thenReturns400() throws Exception {
        AppUser user = createActivatedUser();
        String accessToken = generateAccessToken(user.getId());

        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, "NewPassword!");

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test password change with invalid new password format (no special character)
     */
    @Test
    void whenNewPasswordWithoutSpecialChar_thenReturns400() throws Exception {
        AppUser user = createActivatedUser();
        String accessToken = generateAccessToken(user.getId());

        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, "NewPassword123");

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test password change with new password too short
     */
    @Test
    void whenNewPasswordTooShort_thenReturns400() throws Exception {
        AppUser user = createActivatedUser();
        String accessToken = generateAccessToken(user.getId());

        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, "New1!");

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test password change with empty current password
     */
    @Test
    void whenEmptyCurrentPassword_thenReturns400() throws Exception {
        AppUser user = createActivatedUser();
        String accessToken = generateAccessToken(user.getId());

        PasswordChangeRequest request = new PasswordChangeRequest("", newPassword);

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test password change with empty new password
     */
    @Test
    void whenEmptyNewPassword_thenReturns400() throws Exception {
        AppUser user = createActivatedUser();
        String accessToken = generateAccessToken(user.getId());

        PasswordChangeRequest request = new PasswordChangeRequest(currentPassword, "");

        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test that valid token can be used multiple times
     */
    @Test
    void whenReusingValidToken_thenSucceeds() throws Exception {
        AppUser user = createActivatedUser();
        String token = jwtUtils.generateAccessToken(user.getId());

        // First request
        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordChangeRequest(currentPassword, "NewPassword1!"))))
                .andExpect(status().isNoContent());

        // Second request with same token (password changed, so use new password)
        mockMvc.perform(post(CHANGE_PASSWORD_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordChangeRequest("NewPassword1!", "NewPassword2!"))))
                .andExpect(status().isNoContent());
    }
}
