package com.peter.authnservice.service.impl;

import com.peter.authnservice.domain.dto.TokenPair;
import com.peter.authnservice.domain.entity.AppUser;
import com.peter.authnservice.domain.event.*;
import com.peter.authnservice.exception.*;
import com.peter.authnservice.repository.UserRepository;
import com.peter.authnservice.service.UserService;
import com.peter.authnservice.util.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@Transactional // Utilize Spring's transaction management to roll back the transaction if an exception occurs
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    @Value("${app-name}")
    private String appName;
    @Value("${jwt.verification.expiration}")
    private long verificationTokenExpirationInMilliseconds;
    @Value("${jwt.reset-password.expiration}")
    private long resetPasswordTokenExpirationInMilliseconds;
    @Value("${frontend-url}")
    private String frontendUrl;
    private final KafkaTemplate<String, Event> kafkaTemplate;

    public UserServiceImpl(UserRepository userRepository, JwtUtils jwtUtils, KafkaTemplate<String, Event> kafkaTemplate) {
        this.userRepository = userRepository;
        this.jwtUtils = jwtUtils;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public AppUser register(String firstName, String lastName, String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("This email has been registered.");
        }

        UUID userId = UUID.randomUUID();

        Event Event = new Event(
                new UserDetails(firstName, lastName, email),
                new Email("Account activation on " + appName,
                        "email/verification-email",
                        Map.of(
                                "appName", appName,
                                "firstName", firstName,
                                "lastName", lastName,
                                "verificationLink", frontendUrl + "/activate?token=" + jwtUtils.generateVerificationToken(userId),
                                "expirationHours", verificationTokenExpirationInMilliseconds / 3600000
                        )
                )
        );
        kafkaTemplate.send("user_registration", Event);
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        return userRepository.save(new AppUser(userId, firstName, lastName, email,
                hashedPassword, false));
    }

    @Override
    public void activateAccount(String token) {
        if (!jwtUtils.validateToken(token)) {
            throw new TokenNotValidException("Invalid or expired verification token");
        }
        UUID userId = jwtUtils.getUserIdFromToken(token);
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.getEnabled()) {
            throw new UserAlreadyVerifiedException("This user has already been verified.");
        }
        user.setEnabled(true);
        userRepository.save(user);
    }

    @Override
    public void resendActivationEmail(String email) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EmailNotFoundException("Email not found"));
        if (user.isEnabled()) {
            throw new EmailAlreadyVerifiedException("This email has already been verified.\nPlease log in to the app.");
        }
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        Event resendActivationEvent = new Event(
                new UserDetails(firstName, lastName, email),
                new Email("Account activation on " + appName,
                        "email/verification-email",
                        Map.of(
                                "appName", appName,
                                "firstName", firstName,
                                "lastName", lastName,
                                "verificationLink", frontendUrl + "/activate?token=" + jwtUtils.generateVerificationToken(user.getId()),
                                "expirationHours", verificationTokenExpirationInMilliseconds / 3600000
                        )
                )
        );
        kafkaTemplate.send("resend_activation", resendActivationEvent);
    }

    @Override
    public TokenPair login(String email, String password) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));
        if (!user.isEnabled()) {
            throw new EmailNotVerifiedException("This email has not been verified.\nPlease check your email for the verification link.");
        }
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }
        UUID userId = user.getId();
        String accessToken = jwtUtils.generateAccessToken(userId);
        String refreshToken = jwtUtils.generateRefreshToken(userId);
        return new TokenPair(accessToken, refreshToken);
    }

    @Override
    public void resetPassword(String email) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EmailNotFoundException("Email not found"));
        if (!user.isEnabled()) {
            throw new EmailNotVerifiedException("This email has not been verified.\nPlease check your email for the verification link.");
        }
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        Event passwordResetEvent = new Event(
                new UserDetails(firstName, lastName, email),
                new Email("Reset password on " + appName,
                        "email/reset-password-email",
                        Map.of(
                                "appName", appName,
                                "firstName", firstName,
                                "lastName", lastName,
                                "resetPasswordLink", frontendUrl + "/reset-password?token=" + jwtUtils.generateResetPasswordToken(user.getId()),
                                "expirationMinutes", (int) (resetPasswordTokenExpirationInMilliseconds / 60000)
                        )
                )
        );
        kafkaTemplate.send("password_reset", passwordResetEvent);
    }

    @Override
    public void resetPasswordConfirm(String token, String newPassword) {
        if (!jwtUtils.validateToken(token)) {
            throw new TokenNotValidException("Invalid or expired reset password token");
        }
        UUID userId = jwtUtils.getUserIdFromToken(token);
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (!user.isEnabled()) {
            throw new EmailNotVerifiedException("This email has not been verified.\nPlease check your email for the verification link.");
        }
        String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        user.setPassword(hashedPassword);
        userRepository.save(user);
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        String email = user.getEmail();
        Event passwordResetConfirmEvent = new Event(
                new UserDetails(firstName, lastName, email),
                new Email("Password reset confirmation on " + appName,
                        "email/reset-password-confirm-email",
                        Map.of(
                                "appName", appName,
                                "firstName", firstName,
                                "lastName", lastName
                        )
                )
        );
        kafkaTemplate.send("password_reset_confirm", passwordResetConfirmEvent);
    }

    @Override
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new TokenNotValidException("Invalid or expired token"));
        if (!BCrypt.checkpw(currentPassword, user.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }
        String hashedNewPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        user.setPassword(hashedNewPassword);
        userRepository.save(user);
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        String email = user.getEmail();
        Event passwordChangeEvent = new Event(
                new UserDetails(firstName, lastName, email),
                new Email("Password changed on " + appName,
                        "email/change-password-email",
                        Map.of(
                                "appName", appName,
                                "firstName", firstName,
                                "lastName", lastName
                        )
                )
        );
        kafkaTemplate.send("password_change", passwordChangeEvent);
    }

    @Override
    public String refreshToken(String refreshToken) {
        if (!jwtUtils.validateToken(refreshToken)) {
            throw new TokenNotValidException("Invalid or expired refresh token");
        }

        UUID userId = jwtUtils.getUserIdFromToken(refreshToken);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new TokenNotValidException("Invalid or expired refresh token"));

        if (!user.isEnabled()) {
            throw new TokenNotValidException("Invalid or expired refresh token");
        }

        return jwtUtils.generateAccessToken(userId);
    }
}
