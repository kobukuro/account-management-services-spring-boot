package com.peter.authnservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peter.authnservice.domain.dto.GoogleOAuthLoginRequest;
import com.peter.authnservice.domain.dto.UserLoginResponse;
import com.peter.authnservice.domain.entity.AppUser;
import com.peter.authnservice.domain.entity.AuthenticationType;
import com.peter.authnservice.domain.entity.UserAuthentication;
import com.peter.authnservice.repository.UserAuthenticationRepository;
import com.peter.authnservice.repository.UserRepository;
import com.peter.authnservice.util.JwtUtils;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
public class GoogleOAuthLoginIntegrationTest {

    private static final String GOOGLE_OAUTH_LOGIN_API_PATH = "/api/v1/users/google-oauth-login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserAuthenticationRepository userAuthenticationRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private RestTemplate restTemplate;

    @Autowired
    private Flyway flyway;

    private final String authorizationCode = "test_auth_code";
    private final String redirectUri = "http://localhost:3000/auth/callback";
    private final String googleUserId = "google_user_123";
    private final String email = "test@gmail.com";
    private final String firstName = "John";
    private final String lastName = "Doe";

    @BeforeEach
    void setUp() {
        // Reset database before each test
        flyway.clean();
        flyway.migrate();
    }

    /**
     * Test successful Google OAuth login for new user
     */
    @Test
    void whenValidAuthCodeAndNewUser_thenCreatesUserAndReturnsTokens() throws Exception {
        // Mock Google token exchange
        mockGoogleTokenExchange();

        // Mock Google user info
        mockGoogleUserInfo();

        GoogleOAuthLoginRequest request = new GoogleOAuthLoginRequest(authorizationCode, redirectUri);

        MvcResult result = mockMvc.perform(post(GOOGLE_OAUTH_LOGIN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        UserLoginResponse response = objectMapper.readValue(responseBody, UserLoginResponse.class);
        String accessToken = response.accessToken();
        // Verify tokens are valid
        assertTrue(jwtUtils.validateToken(accessToken));
        assertTrue(jwtUtils.validateToken(response.refreshToken()));

        // Verify user was created in database
        Optional<UserAuthentication> userAuth = userAuthenticationRepository
                .findByProviderIdAndType(googleUserId, AuthenticationType.GOOGLE);
        assertTrue(userAuth.isPresent());
        assertEquals(email, userAuth.get().getEmail());

        AppUser user = userAuth.get().getUser();
        assertEquals(firstName, user.getFirstName());
        assertEquals(lastName, user.getLastName());
        assertTrue(user.getEnabled());

        // Verify JWT contains correct user ID
        UUID userIdFromToken = jwtUtils.getUserIdFromToken(accessToken);
        assertEquals(user.getId(), userIdFromToken);
    }

    /**
     * Test successful Google OAuth login for existing user
     */
    @Test
    void whenValidAuthCodeAndExistingUser_thenReturnsTokens() throws Exception {
        // Create existing user
        UUID existingUserId = UUID.randomUUID();
        AppUser existingUser = new AppUser(existingUserId, firstName, lastName, true);
        userRepository.save(existingUser);

        UserAuthentication existingAuth = UserAuthentication.createOAuthAuth(
                existingUser, AuthenticationType.GOOGLE, googleUserId, email);
        userAuthenticationRepository.save(existingAuth);

        // Mock Google token exchange
        mockGoogleTokenExchange();

        // Mock Google user info
        mockGoogleUserInfo();

        GoogleOAuthLoginRequest request = new GoogleOAuthLoginRequest(authorizationCode, redirectUri);

        MvcResult result = mockMvc.perform(post(GOOGLE_OAUTH_LOGIN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        UserLoginResponse response = objectMapper.readValue(responseBody, UserLoginResponse.class);

        // Verify JWT contains correct user ID
        UUID userIdFromToken = jwtUtils.getUserIdFromToken(response.accessToken());
        assertEquals(existingUserId, userIdFromToken);

        // Verify user count didn't increase (no duplicate user created)
        assertEquals(1, userRepository.count());
    }

    /**
     * Test Google OAuth login with email update for existing user
     */
    @Test
    void whenExistingUserWithDifferentEmail_thenUpdatesEmail() throws Exception {
        // Create existing user with old email
        UUID existingUserId = UUID.randomUUID();
        AppUser existingUser = new AppUser(existingUserId, firstName, lastName, true);
        userRepository.save(existingUser);

        String oldEmail = "old@gmail.com";
        UserAuthentication existingAuth = UserAuthentication.createOAuthAuth(
                existingUser, AuthenticationType.GOOGLE, googleUserId, oldEmail);
        userAuthenticationRepository.save(existingAuth);

        // Mock Google token exchange
        mockGoogleTokenExchange();

        // Mock Google user info with new email
        mockGoogleUserInfo();

        GoogleOAuthLoginRequest request = new GoogleOAuthLoginRequest(authorizationCode, redirectUri);

        mockMvc.perform(post(GOOGLE_OAUTH_LOGIN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify email was updated
        UserAuthentication updatedAuth = userAuthenticationRepository
                .findByProviderIdAndType(googleUserId, AuthenticationType.GOOGLE)
                .orElseThrow();
        assertEquals(email, updatedAuth.getEmail()); // Should be new email
    }

    /**
     * Test Google OAuth login with invalid authorization code
     */
    @Test
    void whenInvalidAuthCode_thenReturns400() throws Exception {
        String errorResponseBody = "{\"error\":\"invalid_grant\"}";

        // Mock HttpClientErrorException
        HttpClientErrorException httpException = new HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                errorResponseBody.getBytes(),
                StandardCharsets.UTF_8
        );

        when(restTemplate.exchange(
                eq("https://oauth2.googleapis.com/token"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenThrow(httpException);

        GoogleOAuthLoginRequest request = new GoogleOAuthLoginRequest("invalid_code", redirectUri);

        mockMvc.perform(post(GOOGLE_OAUTH_LOGIN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenMismatchRedirectUri_thenReturns400() throws Exception {
        String errorResponseBody = "{\"error\":\"redirect_uri_mismatch\"}";

        HttpClientErrorException httpException = new HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                errorResponseBody.getBytes(),
                StandardCharsets.UTF_8
        );

        when(restTemplate.exchange(
                eq("https://oauth2.googleapis.com/token"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenThrow(httpException);

        String mismatchedRedirectUri = "http://localhost:3000/wrong-callback";
        GoogleOAuthLoginRequest request = new GoogleOAuthLoginRequest(authorizationCode, mismatchedRedirectUri);

        mockMvc.perform(post(GOOGLE_OAUTH_LOGIN_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // Helper methods for mocking Google API responses

    private void mockGoogleTokenExchange() {
        Map<String, Object> tokenResponse = new HashMap<>();
        String googleAccessToken = "google_access_token";
        tokenResponse.put("access_token", googleAccessToken);
        tokenResponse.put("token_type", "Bearer");
        tokenResponse.put("expires_in", 3600);

        when(restTemplate.exchange(
                eq("https://oauth2.googleapis.com/token"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenReturn(ResponseEntity.ok(tokenResponse));
    }

    private void mockGoogleUserInfo() {
        Map<String, Object> userInfoResponse = new HashMap<>();
        userInfoResponse.put("id", googleUserId);
        userInfoResponse.put("email", email);
        userInfoResponse.put("verified_email", true);
        userInfoResponse.put("name", firstName + " " + lastName);
        userInfoResponse.put("given_name", firstName);
        userInfoResponse.put("family_name", lastName);
        userInfoResponse.put("picture", "https://example.com/picture.jpg");

        when(restTemplate.exchange(
                eq("https://www.googleapis.com/oauth2/v2/userinfo"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenReturn(ResponseEntity.ok(userInfoResponse));
    }
}
