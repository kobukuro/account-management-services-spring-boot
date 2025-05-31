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

@Component
public class JwtUtils {
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
        algorithm = Algorithm.HMAC256(secret);
        verifier = JWT.require(algorithm).build();
    }

    public String generateVerificationToken(String email) {
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + verificationTokenExpiration);

        return JWT.create()
                .withSubject(email)
                .withIssuedAt(now)
                .withExpiresAt(expiryDate)
                .sign(algorithm);
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

    public String getEmailFromToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }

        try {
            DecodedJWT jwt = verifier.verify(token);
            return jwt.getSubject();
        } catch (JWTVerificationException e) {
            throw new IllegalArgumentException("Invalid token", e);
        }
    }

    public String generateAccessToken(Long userId) {
        return JWT.create()
                .withSubject(Long.toString(userId))
                .withExpiresAt(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .sign(com.auth0.jwt.algorithms.Algorithm.HMAC256(secret));
    }

    public String generateRefreshToken(Long userId) {
        return JWT.create()
                .withSubject(Long.toString(userId))
                .withExpiresAt(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .sign(com.auth0.jwt.algorithms.Algorithm.HMAC256(secret));
    }

    public String generateResetPasswordToken(String email) {
        return JWT.create()
                .withSubject(email)
                .withExpiresAt(new Date(System.currentTimeMillis() + resetPasswordTokenExpiration))
                .sign(com.auth0.jwt.algorithms.Algorithm.HMAC256(secret));
    }
}
