package com.peter.authnservice.controller;

import com.peter.authnservice.domain.dto.PasswordChangeRequest;
import com.peter.authnservice.domain.dto.UpdateProfileRequest;
import com.peter.authnservice.domain.entity.AppUser;
import com.peter.authnservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.ZonedDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    /**
     * Test changePassword when authentication.getName() returns null
     * This covers the branch where userId becomes null
     */
    @Test
    void whenAuthenticationNameIsNull_thenPassesNullToService() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(null);

        PasswordChangeRequest request = new PasswordChangeRequest("currentPass", "NewPass123!");

        userController.changePassword(authentication, request);

        // Verify that null is passed as userId
        verify(userService).changePassword(isNull(), eq("currentPass"), eq("NewPass123!"));
    }

    /**
     * Test updateProfile when authentication.getName() returns null
     * This covers the branch where userId becomes null in updateProfile
     */
    @Test
    void whenAuthenticationNameIsNullInUpdateProfile_thenPassesNullToService() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(null);

        UpdateProfileRequest request = new UpdateProfileRequest("NewFirstName", "NewLastName");

        // Mock the service to return a valid AppUser
        AppUser mockUser = new AppUser("NewFirstName", "NewLastName", true);
        mockUser.setLastUpdatedAt(ZonedDateTime.now());
        when(userService.updateProfile(isNull(), eq("NewFirstName"), eq("NewLastName")))
                .thenReturn(mockUser);

        userController.updateProfile(authentication, request);

        // Verify that null is passed as userId
        verify(userService).updateProfile(isNull(), eq("NewFirstName"), eq("NewLastName"));
    }
}
