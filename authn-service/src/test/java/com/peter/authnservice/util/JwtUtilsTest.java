package com.peter.authnservice.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtUtils class
 * Tests JWT token generation, validation, and user ID extraction
 */

class JwtUtilsTest {

    private JwtUtils jwtUtils;
    // Test secret must be at least 32 characters long for HMAC256 (this is 58 characters)
    private final String testSecret = "test-secret-key-for-jwt-token-generation-and-validation";
    private final long verificationTokenExpiration = 3600000L; // 1 hour
    private final long accessTokenExpiration = 900000L; // 15 minutes
    private final long refreshTokenExpiration = 86400000L; // 24 hours
    private final long resetPasswordTokenExpiration = 300000L; // 5 minutes
    private final UUID testUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", testSecret);
        ReflectionTestUtils.setField(jwtUtils, "verificationTokenExpiration", verificationTokenExpiration);
        ReflectionTestUtils.setField(jwtUtils, "accessTokenExpiration", accessTokenExpiration);
        ReflectionTestUtils.setField(jwtUtils, "refreshTokenExpiration", refreshTokenExpiration);
        ReflectionTestUtils.setField(jwtUtils, "resetPasswordTokenExpiration", resetPasswordTokenExpiration);
    }

    // ==================== INIT METHOD TESTS ====================

    @Test
    void init_withValidSecret_shouldInitializeSuccessfully() {
        assertDoesNotThrow(() -> jwtUtils.init());
    }

    @Test
    void init_withNullSecret_shouldThrowIllegalArgumentException() {
        ReflectionTestUtils.setField(jwtUtils, "secret", null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> jwtUtils.init()
        );

        assertEquals("JWT secret key must not be null or empty.", exception.getMessage());
    }

    @Test
    void init_withEmptySecret_shouldThrowIllegalArgumentException() {
        ReflectionTestUtils.setField(jwtUtils, "secret", "");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> jwtUtils.init()
        );

        assertEquals("JWT secret key must not be null or empty.", exception.getMessage());
    }

    @Test
    void init_withWhitespaceSecret_shouldThrowIllegalArgumentException() {
        ReflectionTestUtils.setField(jwtUtils, "secret", "   ");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> jwtUtils.init()
        );

        assertEquals("JWT secret key must not be null or empty.", exception.getMessage());
    }

    @Test
    void init_withSecretLessThan32Characters_shouldThrowIllegalArgumentException() {
        ReflectionTestUtils.setField(jwtUtils, "secret", "shortSecret");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> jwtUtils.init()
        );

        assertTrue(exception.getMessage().contains("must be at least 32 characters long for HMAC256"));
    }

    @Test
    void init_withSecretExactly32Characters_shouldInitializeSuccessfully() {
        String secretWith32Chars = "12345678901234567890123456789012"; // exactly 32 characters
        ReflectionTestUtils.setField(jwtUtils, "secret", secretWith32Chars);

        assertDoesNotThrow(() -> jwtUtils.init());
    }

    // ==================== TOKEN GENERATION TESTS ====================

    /**
     * Test successful verification token generation
     */
    @Test
    void whenGenerateVerificationToken_thenTokenIsValid() {
        jwtUtils.init();
        String token = jwtUtils.generateVerificationToken(testUserId);

        assertNotNull(token);
        assertTrue(jwtUtils.validateToken(token));
        assertEquals(testUserId, jwtUtils.getUserIdFromToken(token));
    }

    /**
     * Test successful access token generation
     */
    @Test
    void whenGenerateAccessToken_thenTokenIsValid() {
        jwtUtils.init();
        String token = jwtUtils.generateAccessToken(testUserId);

        assertNotNull(token);
        assertTrue(jwtUtils.validateToken(token));
        assertEquals(testUserId, jwtUtils.getUserIdFromToken(token));
    }

    /**
     * Test successful refresh token generation
     */
    @Test
    void whenGenerateRefreshToken_thenTokenIsValid() {
        jwtUtils.init();
        String token = jwtUtils.generateRefreshToken(testUserId);

        assertNotNull(token);
        assertTrue(jwtUtils.validateToken(token));
        assertEquals(testUserId, jwtUtils.getUserIdFromToken(token));
    }

    /**
     * Test successful reset password token generation
     */
    @Test
    void whenGenerateResetPasswordToken_thenTokenIsValid() {
        jwtUtils.init();
        String token = jwtUtils.generateResetPasswordToken(testUserId);

        assertNotNull(token);
        assertTrue(jwtUtils.validateToken(token));
        assertEquals(testUserId, jwtUtils.getUserIdFromToken(token));
    }

    /**
     * Test that tokens generated for different users are distinct
     */
    @Test
    void whenGenerateTokensForDifferentUsers_thenTokensAreDifferent() {
        jwtUtils.init();
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        String token1 = jwtUtils.generateAccessToken(userId1);
        String token2 = jwtUtils.generateAccessToken(userId2);

        assertNotEquals(token1, token2);
        assertEquals(userId1, jwtUtils.getUserIdFromToken(token1));
        assertEquals(userId2, jwtUtils.getUserIdFromToken(token2));
    }

    // ==================== TOKEN VALIDATION TESTS ====================

    /**
     * Test validation of valid token
     */
    @Test
    void whenValidToken_thenValidateReturnsTrue() {
        jwtUtils.init();
        String token = jwtUtils.generateAccessToken(testUserId);
        assertTrue(jwtUtils.validateToken(token));
    }

    /**
     * Test validation of null token
     */
    @Test
    void whenNullToken_thenValidateReturnsFalse() {
        jwtUtils.init();
        assertFalse(jwtUtils.validateToken(null));
    }

    /**
     * Test validation of empty token
     */
    @Test
    void whenEmptyToken_thenValidateReturnsFalse() {
        jwtUtils.init();
        assertFalse(jwtUtils.validateToken(""));
    }

    /**
     * Test validation of malformed token
     */
    @Test
    void whenMalformedToken_thenValidateReturnsFalse() {
        jwtUtils.init();
        assertFalse(jwtUtils.validateToken("this-is-not-a-valid-jwt-token"));
    }

    /**
     * Test validation of token with wrong signature
     */
    @Test
    void whenTokenWithWrongSignature_thenValidateReturnsFalse() {
        jwtUtils.init();
        // Create token with different secret
        Algorithm wrongAlgorithm = Algorithm.HMAC256("wrong-secret-key-for-testing-jwt-validation-purposes");
        String tokenWithWrongSignature = JWT.create()
                .withSubject(testUserId.toString())
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000))
                .sign(wrongAlgorithm);

        assertFalse(jwtUtils.validateToken(tokenWithWrongSignature));
    }

    /**
     * Test validation of expired token
     */
    @Test
    void whenExpiredToken_thenValidateReturnsFalse() {
        jwtUtils.init();
        Algorithm algorithm = Algorithm.HMAC256(testSecret);
        Date past = new Date(System.currentTimeMillis() - 10000); // 10 seconds ago
        String expiredToken = JWT.create()
                .withSubject(testUserId.toString())
                .withIssuedAt(new Date(System.currentTimeMillis() - 20000))
                .withExpiresAt(past)
                .sign(algorithm);

        assertFalse(jwtUtils.validateToken(expiredToken));
    }

    // ==================== GET USER ID FROM TOKEN TESTS ====================

    /**
     * Test extracting user ID from valid token
     */
    @Test
    void whenValidToken_thenExtractCorrectUserId() {
        jwtUtils.init();
        String token = jwtUtils.generateAccessToken(testUserId);
        UUID extractedUserId = jwtUtils.getUserIdFromToken(token);

        assertEquals(testUserId, extractedUserId);
    }

    /**
     * Test extracting user ID from null token
     */
    @Test
    void whenNullToken_thenGetUserIdThrowsException() {
        jwtUtils.init();
        Exception exception = assertThrows(IllegalArgumentException.class, () -> jwtUtils.getUserIdFromToken(null));
        assertEquals("Token cannot be null or empty", exception.getMessage());
    }

    /**
     * Test extracting user ID from empty token
     */
    @Test
    void whenEmptyToken_thenGetUserIdThrowsException() {
        jwtUtils.init();
        Exception exception = assertThrows(IllegalArgumentException.class, () -> jwtUtils.getUserIdFromToken(""));
        assertEquals("Token cannot be null or empty", exception.getMessage());
    }

    /**
     * Test extracting user ID from invalid token
     */
    @Test
    void whenInvalidToken_thenGetUserIdThrowsException() {
        jwtUtils.init();
        Exception exception = assertThrows(IllegalArgumentException.class, () -> jwtUtils.getUserIdFromToken("invalid-token"));
        assertEquals("Invalid token", exception.getMessage());
    }

    /**
     * Test extracting user ID from token with invalid user ID format
     */
    @Test
    void whenTokenWithInvalidUserIdFormat_thenGetUserIdThrowsException() {
        jwtUtils.init();
        Algorithm algorithm = Algorithm.HMAC256(testSecret);
        String tokenWithInvalidUserId = JWT.create()
                .withSubject("not-a-valid-uuid")
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000))
                .sign(algorithm);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> jwtUtils.getUserIdFromToken(tokenWithInvalidUserId));
        assertEquals("Invalid user ID in token", exception.getMessage());
    }

    /**
     * Test extracting user ID from token with null subject
     */
    @Test
    void whenTokenWithNullSubject_thenGetUserIdReturnsNull() {
        jwtUtils.init();
        Algorithm algorithm = Algorithm.HMAC256(testSecret);
        String tokenWithNullSubject = JWT.create()
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000))
                .sign(algorithm);

        UUID extractedUserId = jwtUtils.getUserIdFromToken(tokenWithNullSubject);
        assertNull(extractedUserId);
    }

    /**
     * Test extracting user ID from expired token
     */
    @Test
    void whenExpiredToken_thenGetUserIdThrowsException() {
        jwtUtils.init();
        Algorithm algorithm = Algorithm.HMAC256(testSecret);
        Date past = new Date(System.currentTimeMillis() - 10000);
        String expiredToken = JWT.create()
                .withSubject(testUserId.toString())
                .withIssuedAt(new Date(System.currentTimeMillis() - 20000))
                .withExpiresAt(past)
                .sign(algorithm);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> jwtUtils.getUserIdFromToken(expiredToken));
        assertEquals("Invalid token", exception.getMessage());
    }

    // ==================== TOKEN SUBJECT TESTS ====================

    /**
     * Test that token contains correct subject (user ID)
     */
    @Test
    void whenTokenGenerated_thenSubjectMatchesUserId() {
        jwtUtils.init();
        String token = jwtUtils.generateAccessToken(testUserId);
        UUID extractedUserId = jwtUtils.getUserIdFromToken(token);

        assertEquals(testUserId.toString(), extractedUserId.toString());
    }

    /**
     * Test multiple token generations for same user produce valid tokens
     */
    @Test
    void whenMultipleTokensGeneratedForSameUser_thenAllAreValid() {
        jwtUtils.init();
        String token1 = jwtUtils.generateAccessToken(testUserId);
        String token2 = jwtUtils.generateAccessToken(testUserId);
        String token3 = jwtUtils.generateAccessToken(testUserId);

        assertTrue(jwtUtils.validateToken(token1));
        assertTrue(jwtUtils.validateToken(token2));
        assertTrue(jwtUtils.validateToken(token3));

        assertEquals(testUserId, jwtUtils.getUserIdFromToken(token1));
        assertEquals(testUserId, jwtUtils.getUserIdFromToken(token2));
        assertEquals(testUserId, jwtUtils.getUserIdFromToken(token3));
    }
}
