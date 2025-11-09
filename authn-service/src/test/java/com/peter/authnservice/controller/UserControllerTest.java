package com.peter.authnservice.controller;

import com.peter.authnservice.domain.dto.PasswordChangeRequest;
import com.peter.authnservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

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
}
