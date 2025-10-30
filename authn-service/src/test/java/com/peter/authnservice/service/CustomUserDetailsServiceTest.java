package com.peter.authnservice.service;

import com.peter.authnservice.domain.entity.AppUser;
import com.peter.authnservice.repository.UserRepository;
import com.peter.authnservice.service.impl.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CustomUserDetailsService
 * Tests loading user details by user ID
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private UUID testUserId;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = new AppUser(testUserId, "John", "Doe", true);
    }

    /**
     * Test successful loading of user by user ID
     */
    @Test
    void whenValidUserId_thenReturnsUserDetails() {
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUserId(testUserId);

        assertNotNull(userDetails);
        assertEquals(testUser, userDetails);
        verify(userRepository, times(1)).findById(testUserId);
    }

    /**
     * Test loading user with non-existent user ID
     */
    @Test
    void whenNonExistentUserId_thenThrowsException() {
        UUID nonExistentUserId = UUID.randomUUID();
        when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> customUserDetailsService.loadUserByUserId(nonExistentUserId));

        assertTrue(exception.getMessage().contains("User not found with id: " + nonExistentUserId));
        verify(userRepository, times(1)).findById(nonExistentUserId);
    }

    /**
     * Test that loadUserByUsername returns null (not implemented)
     */
    @Test
    void whenLoadUserByUsername_thenReturnsNull() {
        UserDetails userDetails = customUserDetailsService.loadUserByUsername("test@example.com");
        assertNull(userDetails);
    }

    /**
     * Test loading multiple different users
     */
    @Test
    void whenLoadingMultipleUsers_thenReturnsCorrectUsers() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        AppUser user1 = new AppUser(userId1, "Alice", "Smith", true);
        AppUser user2 = new AppUser(userId2, "Bob", "Jones", true);

        when(userRepository.findById(userId1)).thenReturn(Optional.of(user1));
        when(userRepository.findById(userId2)).thenReturn(Optional.of(user2));

        UserDetails userDetails1 = customUserDetailsService.loadUserByUserId(userId1);
        UserDetails userDetails2 = customUserDetailsService.loadUserByUserId(userId2);

        assertEquals(user1, userDetails1);
        assertEquals(user2, userDetails2);
        verify(userRepository, times(1)).findById(userId1);
        verify(userRepository, times(1)).findById(userId2);
    }

    /**
     * Test loading disabled user
     */
    @Test
    void whenLoadingDisabledUser_thenReturnsUserDetails() {
        AppUser disabledUser = new AppUser(testUserId, "John", "Doe", false);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(disabledUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUserId(testUserId);

        assertNotNull(userDetails);
        assertEquals(disabledUser, userDetails);
        assertFalse(((AppUser) userDetails).getEnabled());
        verify(userRepository, times(1)).findById(testUserId);
    }
}
