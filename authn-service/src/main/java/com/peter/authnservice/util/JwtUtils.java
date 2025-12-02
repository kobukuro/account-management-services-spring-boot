package com.peter.authnservice.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtils {
    /**
     * Minimum secret key length for HMAC256.
     * For optimal security with HMAC256, the key should be at least 32 bytes (256 bits) as recommended by RFC 2104.
     */
    private static final int MINIMUM_SECRET_LENGTH = 32;

    @Value("${secret-key}")
    private String secret;

    @Value("${jwt.verification.expiration}")
    private long verificationTokenExpiration;

    @Value("${jwt.access.expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh.expiration}")
    private long refreshTokenExpiration;

    @Value("${jwt.reset-password.expiration}")
    private long resetPasswordTokenExpiration;

    private Algorithm algorithm;
    private JWTVerifier verifier;

    /**
     * Initializes JWT components after dependency injection is complete.
     * This method will be called only once after the bean is constructed and dependencies are injected.
     * <p>
     * Creates and stores:
     * - Algorithm instance for token signing and verification
     * - JWTVerifier instance for token validation
     * <p>
     * Using @PostConstruct ensures that these expensive objects are created only once
     * and can be reused throughout the lifecycle of this bean, improving performance
     * by avoiding repeated instantiation.
     */
    @PostConstruct
    public void init() {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalArgumentException("JWT secret key must not be null or empty.");
        }
        if (secret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalArgumentException(
                    "JWT secret key must be at least " + MINIMUM_SECRET_LENGTH +
                    " characters long for HMAC256."
            );
        }
        algorithm = Algorithm.HMAC256(secret);
        verifier = JWT.require(algorithm).build();
    }

    private String generateToken(UUID userId, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return JWT.create()
                .withSubject(userId.toString())
                .withIssuedAt(now)
                .withExpiresAt(expiryDate)
                .sign(algorithm);
    }

    public String generateVerificationToken(UUID userId) {
        return generateToken(userId, verificationTokenExpiration);
    }


    public String generateAccessToken(UUID userId) {
        return generateToken(userId, accessTokenExpiration);
    }

    public String generateRefreshToken(UUID userId) {
        return generateToken(userId, refreshTokenExpiration);
    }

    public String generateResetPasswordToken(UUID userId) {
        return generateToken(userId, resetPasswordTokenExpiration);
    }

    public boolean validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        try {
            verifier.verify(token);
            return true;
        } catch (JWTVerificationException e) {
            return false;
        }
    }

    public UUID getUserIdFromToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }

        try {
            DecodedJWT jwt = verifier.verify(token);
            return jwt.getSubject() == null ? null : UUID.fromString(jwt.getSubject());
        } catch (JWTVerificationException e) {
            throw new IllegalArgumentException("Invalid token", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID in token", e);
        }
    }
}
