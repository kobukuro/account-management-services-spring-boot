package com.peter.authnservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peter.authnservice.domain.dto.*;
import com.peter.authnservice.domain.entity.AppUser;
import com.peter.authnservice.domain.entity.AuthenticationType;
import com.peter.authnservice.domain.entity.UserAuthentication;
import com.peter.authnservice.repository.UserAuthenticationRepository;
import com.peter.authnservice.repository.UserRepository;
import com.peter.authnservice.util.JwtUtils;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
public class UserLoginIntegrationTest {

    private static final String REGISTER_API_PATH = "/api/v1/users";
    private static final String LOGIN_API_PATH = "/api/v1/users/login";
    private static final String REFRESH_TOKEN_API_PATH = "/api/v1/users/refresh-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthenticationRepository userAuthRepository;

    @Autowired
    private Flyway flyway;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
        UserAuthentication userAuth = userAuthRepository.findByEmailAndType(email, AuthenticationType.LOCAL).orElseThrow();
        userAuth.setEnabled(true);
        userAuthRepository.save(userAuth);

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

        UserAuthentication userAuth = userAuthRepository.findByEmailAndType(email, AuthenticationType.LOCAL).orElseThrow();
        userAuth.setEnabled(true);
        userAuthRepository.save(userAuth);

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
     * Test login with disabled account
     */
    @Test
    void whenLoginWithDisabledAccount_thenReturns403() throws Exception {
        AppUser user = new AppUser("John", "Doe", false);
        user = userRepository.save(user);

        UserAuthentication userAuth = UserAuthentication.createLocalAuth(user, email, passwordEncoder.encode(password));
        userAuthRepository.save(userAuth);

        UserLoginRequest loginRequest = new UserLoginRequest(email, password);

        mockMvc.perform(post(LOGIN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("User account is disabled."));
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

    // ==================== REFRESH TOKEN TESTS ====================

    /**
     * Test successful token refresh with valid refresh token
     */
    @Test
    void whenValidRefreshToken_thenReturns200AndNewAccessToken() throws Exception {
        // Register user
        UserRegistrationRequest registrationRequest = new UserRegistrationRequest(
                firstName, lastName, email, password
        );
        mockMvc.perform(post(REGISTER_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest)))
                .andExpect(status().isCreated());

        // Verify account
        UserAuthentication userAuth = userAuthRepository.findByEmailAndType(email, AuthenticationType.LOCAL).orElseThrow();
        AppUser user = userAuth.getUser();
        userAuth.setEnabled(true);
        userAuthRepository.save(userAuth);

        // Extract refresh token from login response
        UserLoginRequest loginRequest = new UserLoginRequest(email, password);

        String loginResponseBody = mockMvc.perform(post(LOGIN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn().getResponse().getContentAsString();
        UserLoginResponse loginResponse = objectMapper.readValue(loginResponseBody, UserLoginResponse.class);
        String refreshToken = loginResponse.refreshToken();
        TokenRefreshRequest request = new TokenRefreshRequest(refreshToken);

        MvcResult result = mockMvc.perform(post(REFRESH_TOKEN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        TokenRefreshResponse response = objectMapper.readValue(responseBody, TokenRefreshResponse.class);

        // Verify the new access token is valid and contains correct user ID
        assertTrue(jwtUtils.validateToken(response.accessToken()));
        assertEquals(user.getId(), jwtUtils.getUserIdFromToken(response.accessToken()));
    }

    /**
     * Test refresh token with invalid token format
     */
    @Test
    void whenInvalidTokenFormat_thenReturns401() throws Exception {
        TokenRefreshRequest request = new TokenRefreshRequest("invalid-token-format");

        mockMvc.perform(post(REFRESH_TOKEN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test refresh token with token containing non-existent user ID
     */
    @Test
    void whenTokenWithNonExistentUserId_thenReturns401() throws Exception {
        // Generate token with non-existent user ID
        UUID nonExistentUserId = UUID.randomUUID();
        String tokenWithNonExistentUser = jwtUtils.generateRefreshToken(nonExistentUserId);

        TokenRefreshRequest request = new TokenRefreshRequest(tokenWithNonExistentUser);

        mockMvc.perform(post(REFRESH_TOKEN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test refresh token with empty token
     */
    @Test
    void whenEmptyRefreshToken_thenReturns400() throws Exception {
        TokenRefreshRequest request = new TokenRefreshRequest("");

        mockMvc.perform(post(REFRESH_TOKEN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test refresh token with null token
     */
    @Test
    void whenNullRefreshToken_thenReturns400() throws Exception {
        TokenRefreshRequest request = new TokenRefreshRequest(null);

        mockMvc.perform(post(REFRESH_TOKEN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test refresh token with disabled account
     */
    @Test
    void whenRefreshTokenWithDisabledAccount_thenReturns403() throws Exception {
        AppUser user = new AppUser("John", "Doe", false);
        user = userRepository.save(user);

        UserAuthentication userAuth = UserAuthentication.createLocalAuth(
                user,
                email,
                passwordEncoder.encode(password)
        );
        userAuthRepository.save(userAuth);

        String refreshToken = jwtUtils.generateRefreshToken(user.getId());
        TokenRefreshRequest request = new TokenRefreshRequest(refreshToken);

        mockMvc.perform(post(REFRESH_TOKEN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
