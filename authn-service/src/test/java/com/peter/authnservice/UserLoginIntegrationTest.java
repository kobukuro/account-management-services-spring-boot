package com.peter.authnservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peter.authnservice.domain.dto.UserLoginRequest;
import com.peter.authnservice.domain.dto.UserRegistrationRequest;
import com.peter.authnservice.domain.entity.AppUser;
import com.peter.authnservice.repository.UserRepository;
import org.flywaydb.core.Flyway;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
public class UserLoginIntegrationTest {

    private static final String REGISTER_API_PATH = "/api/v1/users";
    private static final String LOGIN_API_PATH = "/api/v1/users/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Flyway flyway;

    private final String firstName = "John";
    private final String lastName = "Doe";
    private final String email = "test@example.com";
    private final String password = "Password123!";

    @BeforeEach
    void setUp() {
        // Reset database before each test
        flyway.clean();
        flyway.migrate();

    }

    /**
     * Test successful login with verified account
     */
    @Test
    void whenValidCredentialsAndVerifiedAccount_thenReturnsTokens() throws Exception {
        // Register user
        UserRegistrationRequest registrationRequest = new UserRegistrationRequest(
                firstName, lastName, email, password
        );
        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest)))
                .andExpect(status().isCreated());

        // Verify account
        AppUser user = userRepository.findByEmail(email).orElseThrow();
        user.setEnabled(true);
        userRepository.save(user);

        // Login
        UserLoginRequest loginRequest = new UserLoginRequest(email, password);
        mockMvc.perform(post(LOGIN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    /**
     * Test login with unverified account
     */
    @Test
    void whenUnverifiedAccount_thenReturns403() throws Exception {
        // Register user without verification
        UserRegistrationRequest registrationRequest = new UserRegistrationRequest(
                firstName, lastName, email, password
        );
        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest)))
                .andExpect(status().isCreated());

        // Try to log in
        UserLoginRequest loginRequest = new UserLoginRequest(email, password);
        mockMvc.perform(post(LOGIN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden());
    }

    /**
     * Test login with incorrect password
     */
    @Test
    void whenIncorrectPassword_thenReturns401() throws Exception {
        // Register and verify user
        UserRegistrationRequest registrationRequest = new UserRegistrationRequest(
                firstName, lastName, email, password
        );
        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest)))
                .andExpect(status().isCreated());

        AppUser user = userRepository.findByEmail(email).orElseThrow();
        user.setEnabled(true);
        userRepository.save(user);

        // Try to log in with wrong password
        UserLoginRequest loginRequest = new UserLoginRequest(email, "WrongPassword123!");
        mockMvc.perform(post(LOGIN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test login with non-existent email
     */
    @Test
    void whenNonExistentEmail_thenReturns401() throws Exception {
        UserLoginRequest loginRequest = new UserLoginRequest(
                "nonexistent@example.com",
                password
        );
        mockMvc.perform(post(LOGIN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test login with empty email
     */
    @Test
    void whenEmptyEmail_thenReturns400() throws Exception {
        UserLoginRequest loginRequest = new UserLoginRequest("", password);
        mockMvc.perform(post(LOGIN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test login with empty password
     */
    @Test
    void whenEmptyPassword_thenReturns400() throws Exception {
        UserLoginRequest loginRequest = new UserLoginRequest(email, "");
        mockMvc.perform(post(LOGIN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
    }
}
