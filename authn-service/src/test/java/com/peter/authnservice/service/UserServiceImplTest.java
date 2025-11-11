package com.peter.authnservice.service;

import com.peter.authnservice.domain.entity.AppUser;
import com.peter.authnservice.exception.TokenNotValidException;
import com.peter.authnservice.exception.UserAccountDisabledException;
import com.peter.authnservice.exception.UserNotFoundException;
import com.peter.authnservice.repository.UserRepository;
import com.peter.authnservice.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private UserServiceImpl userService;

    /**
     * Test changePassword when user is not found in repository
     */
    @Test
    void whenChangePasswordWithNonExistentUser_thenThrowsTokenNotValidException() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(TokenNotValidException.class, () -> userService.changePassword(userId, "currentPassword", "newPassword"));
    }

    /**
     * Test exchangeCodeForAccessToken when response status is not 2xx
     * This covers the edge case where RestTemplate doesn't throw an exception but returns non-2xx
     */
    @Test
    void whenTokenExchangeReturnsNon2xxWithoutException_thenThrowsOAuthException() {
        // Mock a non-2xx response (e.g., 3xx redirect) that doesn't throw exception
        ResponseEntity<Map<String, Object>> redirectResponse =
                ResponseEntity.status(HttpStatus.FOUND).body(null);

        when(restTemplate.exchange(
                eq("https://oauth2.googleapis.com/token"),
                eq(HttpMethod.POST),
                any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenReturn(redirectResponse);

        assertThrows(Exception.class, () -> userService.googleOAuthLogin("auth_code", "redirect_uri"));
    }

    /**
     * Test getUserInfoFromGoogle when response body is null despite 2xx status
     */
    @Test
    void whenGetUserInfoReturnsNullBody_thenThrowsOAuthException() {
        // First mock successful token exchange
        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", "test_token");
        when(restTemplate.exchange(
                eq("https://oauth2.googleapis.com/token"),
                eq(HttpMethod.POST),
                any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenReturn(ResponseEntity.ok(tokenResponse));

        // Mock getUserInfo returning 2xx but null body
        ResponseEntity<Map<String, Object>> nullBodyResponse =
                ResponseEntity.ok(null);

        when(restTemplate.exchange(
                eq("https://www.googleapis.com/oauth2/v2/userinfo"),
                eq(HttpMethod.GET),
                any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenReturn(nullBodyResponse);

        assertThrows(Exception.class, () -> userService.googleOAuthLogin("auth_code", "redirect_uri"));
    }

    /**
     * Test getUserInfoFromGoogle when response returns non-2xx status
     */
    @Test
    void whenGetUserInfoReturnsNon2xxStatus_thenThrowsOAuthException() {
        // First mock successful token exchange
        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", "test_token");
        when(restTemplate.exchange(
                eq("https://oauth2.googleapis.com/token"),
                eq(HttpMethod.POST),
                any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenReturn(ResponseEntity.ok(tokenResponse));

        // Mock getUserInfo returning non-2xx status (e.g., 3xx redirect) without throwing exception
        ResponseEntity<Map<String, Object>> redirectResponse =
                ResponseEntity.status(HttpStatus.FOUND).body(null);

        when(restTemplate.exchange(
                eq("https://www.googleapis.com/oauth2/v2/userinfo"),
                eq(HttpMethod.GET),
                any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenReturn(redirectResponse);

        assertThrows(Exception.class, () -> userService.googleOAuthLogin("auth_code", "redirect_uri"));
    }

    /**
     * Test updateProfile when user is not found
     */
    @Test
    void whenUpdateProfileWithNonExistentUser_thenThrowsUserNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> userService.updateProfile(userId, "NewFirstName", "NewLastName"));
    }

    /**
     * Test updateProfile when user account is disabled
     */
    @Test
    void whenUpdateProfileWithDisabledAccount_thenThrowsUserAccountDisabledException() {
        UUID userId = UUID.randomUUID();
        AppUser disabledUser = new AppUser(userId, "John", "Doe", false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(disabledUser));

        assertThrows(UserAccountDisabledException.class,
                () -> userService.updateProfile(userId, "NewFirstName", "NewLastName"));
    }

    /**
     * Test updateProfile when no fields are provided
     */
    @Test
    void whenUpdateProfileWithNoFields_thenThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "John", "Doe", true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateProfile(userId, null, null));
    }

    /**
     * Test updateProfile when only blank fields are provided
     */
    @Test
    void whenUpdateProfileWithOnlyBlankFields_thenThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "John", "Doe", true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateProfile(userId, "   ", ""));
    }

    /**
     * Test updateProfile successfully updates both firstName and lastName
     */
    @Test
    void whenUpdateProfileWithBothFields_thenSuccessfullyUpdates() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "John", "Doe", true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(AppUser.class))).thenReturn(user);

        AppUser updatedUser = userService.updateProfile(userId, "Jane", "Smith");

        assertEquals("Jane", updatedUser.getFirstName());
        assertEquals("Smith", updatedUser.getLastName());
        verify(userRepository, times(1)).save(user);
    }

    /**
     * Test updateProfile successfully updates only firstName
     */
    @Test
    void whenUpdateProfileWithOnlyFirstName_thenSuccessfullyUpdates() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "John", "Doe", true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(AppUser.class))).thenReturn(user);

        AppUser updatedUser = userService.updateProfile(userId, "Jane", null);

        assertEquals("Jane", updatedUser.getFirstName());
        assertEquals("Doe", updatedUser.getLastName()); // lastName unchanged
        verify(userRepository, times(1)).save(user);
    }

    /**
     * Test updateProfile successfully updates only lastName
     */
    @Test
    void whenUpdateProfileWithOnlyLastName_thenSuccessfullyUpdates() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "John", "Doe", true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(AppUser.class))).thenReturn(user);

        AppUser updatedUser = userService.updateProfile(userId, null, "Smith");

        assertEquals("John", updatedUser.getFirstName()); // firstName unchanged
        assertEquals("Smith", updatedUser.getLastName());
        verify(userRepository, times(1)).save(user);
    }
}
