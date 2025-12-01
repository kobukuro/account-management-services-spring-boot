package com.peter.authnservice;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peter.authnservice.domain.dto.UpdateProfileRequest;
import com.peter.authnservice.domain.entity.AppUser;
import com.peter.authnservice.domain.entity.UserAuthentication;
import com.peter.authnservice.repository.UserAuthenticationRepository;
import com.peter.authnservice.repository.UserRepository;
import com.peter.authnservice.util.JwtUtils;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.UUID;

import static com.peter.authnservice.domain.entity.UserAuthentication.createLocalAuth;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
public class UpdateProfileIntegrationTest {

    private static final String UPDATE_PROFILE_API_PATH = "/api/v1/users/me";

    @Value("${secret-key}")
    private String secretKey;

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final String firstName = "John";
    private final String lastName = "Doe";
    private final String testEmail = "test@example.com";
    private final String testPassword = "Password123!";

    private UUID testUserId;
    private String accessToken;

    @BeforeEach
    void setUp() {
        flyway.clean();
        flyway.migrate();

        // Create test user
        testUserId = UUID.randomUUID();
        AppUser testUser = new AppUser(testUserId, firstName, lastName, true);
        userRepository.save(testUser);

        String hashedPassword = passwordEncoder.encode(testPassword);
        UserAuthentication userAuth = createLocalAuth(testUser, testEmail, hashedPassword);
        userAuthenticationRepository.save(userAuth);

        // Generate access token
        accessToken = jwtUtils.generateAccessToken(testUserId);
    }

    @AfterEach
    void tearDown() {
        userAuthenticationRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * Test successful profile update with both firstName and lastName
     */
    @Test
    void whenUpdateProfileWithBothFields_thenReturns200AndUpdatedProfile() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Jane", "Smith");

        mockMvc.perform(patch(UPDATE_PROFILE_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testUserId.toString()))
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Smith"))
                .andExpect(jsonPath("$.lastUpdatedAt").exists())
                .andReturn();

        // Verify database was updated
        AppUser updatedUser = userRepository.findById(testUserId).orElseThrow();
        assertEquals("Jane", updatedUser.getFirstName());
        assertEquals("Smith", updatedUser.getLastName());
    }

    /**
     * Test successful profile update with only firstName
     */
    @Test
    void whenUpdateProfileWithOnlyFirstName_thenReturns200AndUpdatesOnlyFirstName() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Jane", null);

        mockMvc.perform(patch(UPDATE_PROFILE_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value(lastName)); // Unchanged

        // Verify database
        AppUser updatedUser = userRepository.findById(testUserId).orElseThrow();
        assertEquals("Jane", updatedUser.getFirstName());
        assertEquals(lastName, updatedUser.getLastName());
    }

    /**
     * Test successful profile update with only lastName
     */
    @Test
    void whenUpdateProfileWithOnlyLastName_thenReturns200AndUpdatesOnlyLastName() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest(null, "Smith");

        mockMvc.perform(patch(UPDATE_PROFILE_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value(firstName)) // Unchanged
                .andExpect(jsonPath("$.lastName").value("Smith"));

        // Verify database
        AppUser updatedUser = userRepository.findById(testUserId).orElseThrow();
        assertEquals(firstName, updatedUser.getFirstName());
        assertEquals("Smith", updatedUser.getLastName());
    }

    /**
     * Test update profile without authentication token
     */
    @Test
    void whenUpdateProfileWithoutToken_thenReturns401() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Jane", "Smith");

        mockMvc.perform(patch(UPDATE_PROFILE_API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test update profile with invalid token
     */
    @Test
    void whenUpdateProfileWithInvalidToken_thenReturns401() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Jane", "Smith");

        mockMvc.perform(patch(UPDATE_PROFILE_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test update profile with no fields provided (both null)
     * This will pass validation but fail at service level with IllegalArgumentException
     * which gets mapped to 400 Bad Request by GlobalExceptionHandler
     */
    @Test
    void whenUpdateProfileWithNoFields_thenReturns400() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest(null, null);

        mockMvc.perform(patch(UPDATE_PROFILE_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test update profile with blank fields
     */
    @Test
    void whenUpdateProfileWithBlankFields_thenReturns400() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("   ", "");

        mockMvc.perform(patch(UPDATE_PROFILE_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test update profile with firstName exceeding max length
     */
    @Test
    void whenUpdateProfileWithTooLongFirstName_thenReturns400() throws Exception {
        String tooLongName = "a".repeat(101); // Exceeds max of 100
        UpdateProfileRequest request = new UpdateProfileRequest(tooLongName, "Smith");

        mockMvc.perform(patch(UPDATE_PROFILE_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test update profile with lastName exceeding max length
     */
    @Test
    void whenUpdateProfileWithTooLongLastName_thenReturns400() throws Exception {
        String tooLongName = "a".repeat(101); // Exceeds max of 100
        UpdateProfileRequest request = new UpdateProfileRequest("Jane", tooLongName);

        mockMvc.perform(patch(UPDATE_PROFILE_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test update profile for disabled user account
     */
    @Test
    void whenUpdateProfileForDisabledUser_thenReturns403() throws Exception {
        // Disable the user
        AppUser user = userRepository.findById(testUserId).orElseThrow();
        user.setEnabled(false);
        userRepository.save(user);

        UpdateProfileRequest request = new UpdateProfileRequest("Jane", "Smith");

        mockMvc.perform(patch(UPDATE_PROFILE_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
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

        UpdateProfileRequest request = new UpdateProfileRequest("Jane", "Smith");

        mockMvc.perform(patch(UPDATE_PROFILE_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithInvalidUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test with token missing subject
     */
    @Test
    void whenTokenWithNullSubject_thenReturns401() throws Exception {
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String tokenWithInvalidUserId = JWT.create()
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000))
                .sign(algorithm);

        UpdateProfileRequest request = new UpdateProfileRequest("Jane", "Smith");

        mockMvc.perform(patch(UPDATE_PROFILE_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithInvalidUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
