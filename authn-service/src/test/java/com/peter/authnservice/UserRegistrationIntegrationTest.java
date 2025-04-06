package com.peter.authnservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peter.authnservice.domain.dto.UserActivationRequest;
import com.peter.authnservice.domain.dto.UserRegistrationRequest;
import com.peter.authnservice.domain.entity.AppUser;
import com.peter.authnservice.repository.UserRepository;
import com.peter.authnservice.util.JwtUtils;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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

    private UserRegistrationRequest validRequest;
    private String firstName;
    private String lastName;
    private String testEmail;

    @BeforeEach
    void setUp() {
        firstName = "John";
        lastName = "Doe";
        testEmail = "test@example.com";
        validRequest = new UserRegistrationRequest(
                firstName,
                lastName,
                testEmail,
                "Password123!"
        );

        // Reset database before each test
        flyway.clean();
        flyway.migrate();
    }

    @AfterEach
    void cleanup() {
        userRepository.deleteAll();
    }

    /**
     * Test normal registration flow
     */
    @Test
    void whenValidInput_thenReturns201() throws Exception {
        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(validRequest.email()));

        assertTrue(userRepository.findByEmail(validRequest.email()).isPresent());
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
                "Password123!"
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
                "Password123!"
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
                "Password123!"
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
                "Password123!"
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
}