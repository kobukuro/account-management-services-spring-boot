package com.peter.authnservice.service;

import com.peter.authnservice.domain.dto.TokenPair;
import com.peter.authnservice.domain.entity.AppUser;

import java.util.UUID;

public interface UserService {
    AppUser register(String firstName, String lastName, String email, String password);

    void activateAccount(String token);

    void resendActivationEmail(String email);

    TokenPair login(String email, String password);

    void resetPassword(String email);

    void resetPasswordConfirm(String token, String password);

    void changePassword(UUID userId, String currentPassword, String newPassword);

    String refreshToken(String refreshToken);
}
