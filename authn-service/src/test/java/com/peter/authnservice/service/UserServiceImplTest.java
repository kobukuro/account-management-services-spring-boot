package com.peter.authnservice.service;

import com.peter.authnservice.domain.dto.ProcessedImage;
import com.peter.authnservice.domain.entity.AppUser;
import com.peter.authnservice.domain.entity.AuthenticationType;
import com.peter.authnservice.domain.entity.UserAuthentication;
import com.peter.authnservice.exception.*;
import com.peter.authnservice.repository.UserAuthenticationRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@ActiveProfiles("ci")
public class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAuthenticationRepository userAuthenticationRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ImageProcessingService imageProcessingService;

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

    // ==================== uploadProfilePicture Tests ====================

    /**
     * Test uploadProfilePicture when user is not found
     */
    @Test
    void whenUploadProfilePictureWithNonExistentUser_thenThrowsUserNotFoundException() {
        UUID userId = UUID.randomUUID();
        MultipartFile file = mock(MultipartFile.class);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.uploadProfilePicture(userId, file));
        verify(userRepository, times(1)).findById(userId);
        verifyNoInteractions(imageProcessingService, fileStorageService);
    }

    /**
     * Test uploadProfilePicture when user account is disabled
     */
    @Test
    void whenUploadProfilePictureWithDisabledUser_thenThrowsUserAccountDisabledException() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "John", "Doe", false); // disabled
        MultipartFile file = mock(MultipartFile.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(UserAccountDisabledException.class, () -> userService.uploadProfilePicture(userId, file));
        verify(userRepository, times(1)).findById(userId);
        verifyNoInteractions(imageProcessingService, fileStorageService);
    }

    /**
     * Test uploadProfilePicture when user has only LOCAL auth and it's not verified
     */
    @Test
    void whenUploadProfilePictureWithUnverifiedLocalAuth_thenThrowsEmailNotVerifiedException() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "John", "Doe", true);
        MultipartFile file = mock(MultipartFile.class);

        UserAuthentication localAuth = UserAuthentication.createLocalAuth(user, "john@example.com", "Password123!");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userAuthenticationRepository.findAllByUserId(userId)).thenReturn(List.of(localAuth));

        assertThrows(EmailNotVerifiedException.class, () -> userService.uploadProfilePicture(userId, file));
        verify(userRepository, times(1)).findById(userId);
        verify(userAuthenticationRepository, times(1)).findAllByUserId(userId);
        verifyNoInteractions(imageProcessingService, fileStorageService);
    }

    /**
     * Test uploadProfilePicture successfully uploads with verified LOCAL auth
     */
    @Test
    void whenUploadProfilePictureWithVerifiedLocalAuth_thenSuccessfullyUploads() throws IOException {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "John", "Doe", true);
        MultipartFile file = mock(MultipartFile.class);

        UserAuthentication localAuth = UserAuthentication.createLocalAuth(user, "john@example.com", "Password123!");
        localAuth.setEnabled(true); // Verified

        ProcessedImage processedImage = new ProcessedImage(
                new ByteArrayInputStream(new byte[100]),
                100L,
                "image/png"
        );

        String expectedUrl = "https://bucket.s3.region.amazonaws.com/profile-pictures/" + userId + "/test.png";

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userAuthenticationRepository.findAllByUserId(userId)).thenReturn(List.of(localAuth));
        when(file.getOriginalFilename()).thenReturn("test.jpg");
        doNothing().when(imageProcessingService).validateFileSize(file);
        doNothing().when(imageProcessingService).validateContentType(file);
        doNothing().when(imageProcessingService).validateFileExtension(anyString());
        doNothing().when(imageProcessingService).validateMagicNumber(file);
        when(imageProcessingService.processImage(file)).thenReturn(processedImage);
        when(fileStorageService.uploadFile(anyString(), any(), anyString(), anyLong())).thenReturn(expectedUrl);
        when(userRepository.save(any(AppUser.class))).thenReturn(user);

        AppUser result = userService.uploadProfilePicture(userId, file);

        assertNotNull(result);
        assertEquals(expectedUrl, user.getProfilePictureUrl());
        verify(imageProcessingService).validateFileSize(file);
        verify(imageProcessingService).validateContentType(file);
        verify(imageProcessingService).validateFileExtension(anyString());
        verify(imageProcessingService).validateMagicNumber(file);
        verify(imageProcessingService).processImage(file);
        verify(fileStorageService).uploadFile(anyString(), any(), eq("image/png"), eq(100L));
        verify(userRepository).save(user);
    }

    /**
     * Test uploadProfilePicture successfully uploads with OAuth authentication
     */
    @Test
    void whenUploadProfilePictureWithOAuthAuth_thenSuccessfullyUploads() throws IOException {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "John", "Doe", true);
        MultipartFile file = mock(MultipartFile.class);

        UserAuthentication googleAuth = UserAuthentication.createOAuthAuth(user, AuthenticationType.GOOGLE, "google-uid-123", "john@example.com");

        ProcessedImage processedImage = new ProcessedImage(
                new ByteArrayInputStream(new byte[100]),
                100L,
                "image/png"
        );

        String expectedUrl = "https://bucket.s3.region.amazonaws.com/profile-pictures/" + userId + "/test.png";

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userAuthenticationRepository.findAllByUserId(userId)).thenReturn(List.of(googleAuth));
        when(file.getOriginalFilename()).thenReturn("test.jpg");
        doNothing().when(imageProcessingService).validateFileSize(file);
        doNothing().when(imageProcessingService).validateContentType(file);
        doNothing().when(imageProcessingService).validateFileExtension(anyString());
        doNothing().when(imageProcessingService).validateMagicNumber(file);
        when(imageProcessingService.processImage(file)).thenReturn(processedImage);
        when(fileStorageService.uploadFile(anyString(), any(), anyString(), anyLong())).thenReturn(expectedUrl);
        when(userRepository.save(any(AppUser.class))).thenReturn(user);

        AppUser result = userService.uploadProfilePicture(userId, file);

        assertNotNull(result);
        assertEquals(expectedUrl, user.getProfilePictureUrl());
        verify(fileStorageService).uploadFile(anyString(), any(), eq("image/png"), eq(100L));
        verify(userRepository).save(user);
    }

    /**
     * Test uploadProfilePicture replaces old profile picture
     */
    @Test
    void whenUploadProfilePictureWithExistingPicture_thenReplacesOldPicture() throws IOException {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "John", "Doe", true);
        String oldPictureUrl = "https://bucket.s3.region.amazonaws.com/profile-pictures/" + userId + "/old.png";
        user.setProfilePictureUrl(oldPictureUrl);
        MultipartFile file = mock(MultipartFile.class);

        UserAuthentication googleAuth = UserAuthentication.createOAuthAuth(user, AuthenticationType.GOOGLE, "google-uid-123", "john@example.com");

        ProcessedImage processedImage = new ProcessedImage(
                new ByteArrayInputStream(new byte[100]),
                100L,
                "image/png"
        );

        String oldKey = "profile-pictures/" + userId + "/old.png";
        String expectedUrl = "https://bucket.s3.region.amazonaws.com/profile-pictures/" + userId + "/new.png";

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userAuthenticationRepository.findAllByUserId(userId)).thenReturn(List.of(googleAuth));
        when(file.getOriginalFilename()).thenReturn("test.jpg");
        doNothing().when(imageProcessingService).validateFileSize(file);
        doNothing().when(imageProcessingService).validateContentType(file);
        doNothing().when(imageProcessingService).validateFileExtension(anyString());
        doNothing().when(imageProcessingService).validateMagicNumber(file);
        when(imageProcessingService.processImage(file)).thenReturn(processedImage);
        when(fileStorageService.extractKeyFromUrl(oldPictureUrl)).thenReturn(oldKey);
        doNothing().when(fileStorageService).deleteFile(oldKey);
        when(fileStorageService.uploadFile(anyString(), any(), anyString(), anyLong())).thenReturn(expectedUrl);
        when(userRepository.save(any(AppUser.class))).thenReturn(user);

        AppUser result = userService.uploadProfilePicture(userId, file);

        assertNotNull(result);
        assertEquals(expectedUrl, user.getProfilePictureUrl());
        verify(fileStorageService).extractKeyFromUrl(oldPictureUrl);
        verify(fileStorageService).deleteFile(oldKey);
        verify(fileStorageService).uploadFile(anyString(), any(), eq("image/png"), eq(100L));
        verify(userRepository).save(user);
    }

    /**
     * Test uploadProfilePicture when file validation fails
     */
    @Test
    void whenUploadProfilePictureWithInvalidFile_thenThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "John", "Doe", true);
        MultipartFile file = mock(MultipartFile.class);

        UserAuthentication googleAuth = UserAuthentication.createOAuthAuth(user, AuthenticationType.GOOGLE, "google-uid-123", "john@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userAuthenticationRepository.findAllByUserId(userId)).thenReturn(List.of(googleAuth));
        doThrow(new IllegalArgumentException("File size exceeds maximum allowed size")).when(imageProcessingService).validateFileSize(file);

        assertThrows(IllegalArgumentException.class, () -> userService.uploadProfilePicture(userId, file));
        verify(imageProcessingService).validateFileSize(file);
        verifyNoInteractions(fileStorageService);
        verify(userRepository, never()).save(any());
    }

    /**
     * Test uploadProfilePicture when image processing fails
     */
    @Test
    void whenUploadProfilePictureWithProcessingError_thenThrowsFileUploadException() throws IOException {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "John", "Doe", true);
        MultipartFile file = mock(MultipartFile.class);

        UserAuthentication googleAuth = UserAuthentication.createOAuthAuth(user, AuthenticationType.GOOGLE, "google-uid-123", "john@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userAuthenticationRepository.findAllByUserId(userId)).thenReturn(List.of(googleAuth));
        when(file.getOriginalFilename()).thenReturn("test.jpg");
        doNothing().when(imageProcessingService).validateFileSize(file);
        doNothing().when(imageProcessingService).validateContentType(file);
        doNothing().when(imageProcessingService).validateFileExtension(anyString());
        doNothing().when(imageProcessingService).validateMagicNumber(file);
        when(imageProcessingService.processImage(file)).thenThrow(new IOException("Processing failed"));

        assertThrows(FileUploadException.class, () -> userService.uploadProfilePicture(userId, file));
        verify(imageProcessingService).processImage(file);
        verifyNoInteractions(fileStorageService);
        verify(userRepository, never()).save(any());
    }

    /**
     * Test uploadProfilePicture when deleting old picture fails (should continue with upload)
     */
    @Test
    void whenUploadProfilePictureAndOldPictureDeletionFails_thenContinuesWithUpload() throws IOException {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "John", "Doe", true);
        String oldPictureUrl = "https://bucket.s3.region.amazonaws.com/profile-pictures/" + userId + "/old.png";
        user.setProfilePictureUrl(oldPictureUrl);
        MultipartFile file = mock(MultipartFile.class);

        UserAuthentication googleAuth = UserAuthentication.createOAuthAuth(user, AuthenticationType.GOOGLE, "google-uid-123", "john@example.com");

        ProcessedImage processedImage = new ProcessedImage(
                new ByteArrayInputStream(new byte[100]),
                100L,
                "image/png"
        );

        String oldKey = "profile-pictures/" + userId + "/old.png";
        String expectedUrl = "https://bucket.s3.region.amazonaws.com/profile-pictures/" + userId + "/new.png";

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userAuthenticationRepository.findAllByUserId(userId)).thenReturn(List.of(googleAuth));
        when(file.getOriginalFilename()).thenReturn("test.jpg");
        doNothing().when(imageProcessingService).validateFileSize(file);
        doNothing().when(imageProcessingService).validateContentType(file);
        doNothing().when(imageProcessingService).validateFileExtension(anyString());
        doNothing().when(imageProcessingService).validateMagicNumber(file);
        when(imageProcessingService.processImage(file)).thenReturn(processedImage);
        when(fileStorageService.extractKeyFromUrl(oldPictureUrl)).thenReturn(oldKey);
        // Simulate deletion failure
        doThrow(new RuntimeException("S3 deletion failed")).when(fileStorageService).deleteFile(oldKey);
        when(fileStorageService.uploadFile(anyString(), any(), anyString(), anyLong())).thenReturn(expectedUrl);
        when(userRepository.save(any(AppUser.class))).thenReturn(user);

        // Should NOT throw exception - deletion failure should be logged but not fail the upload
        AppUser result = userService.uploadProfilePicture(userId, file);

        assertNotNull(result);
        assertEquals(expectedUrl, user.getProfilePictureUrl());
        verify(fileStorageService).extractKeyFromUrl(oldPictureUrl);
        verify(fileStorageService).deleteFile(oldKey); // Attempted deletion
        verify(fileStorageService).uploadFile(anyString(), any(), eq("image/png"), eq(100L)); // Upload still succeeded
        verify(userRepository).save(user);
    }

    /**
     * Test uploadProfilePicture when upload to storage fails with generic exception
     */
    @Test
    void whenUploadProfilePictureAndStorageUploadFails_thenThrowsFileUploadException() throws IOException {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "John", "Doe", true);
        MultipartFile file = mock(MultipartFile.class);

        UserAuthentication googleAuth = UserAuthentication.createOAuthAuth(user, AuthenticationType.GOOGLE, "google-uid-123", "john@example.com");

        ProcessedImage processedImage = new ProcessedImage(
                new ByteArrayInputStream(new byte[100]),
                100L,
                "image/png"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userAuthenticationRepository.findAllByUserId(userId)).thenReturn(List.of(googleAuth));
        when(file.getOriginalFilename()).thenReturn("test.jpg");
        doNothing().when(imageProcessingService).validateFileSize(file);
        doNothing().when(imageProcessingService).validateContentType(file);
        doNothing().when(imageProcessingService).validateFileExtension(anyString());
        doNothing().when(imageProcessingService).validateMagicNumber(file);
        when(imageProcessingService.processImage(file)).thenReturn(processedImage);
        // Simulate storage upload failure with generic exception
        when(fileStorageService.uploadFile(anyString(), any(), anyString(), anyLong()))
                .thenThrow(new RuntimeException("S3 connection failed"));

        FileUploadException exception = assertThrows(FileUploadException.class,
                () -> userService.uploadProfilePicture(userId, file));

        assertTrue(exception.getMessage().contains("Failed to upload profile picture"));
        verify(imageProcessingService).processImage(file);
        verify(fileStorageService).uploadFile(anyString(), any(), eq("image/png"), eq(100L));
        verify(userRepository, never()).save(any());
    }

    /**
     * Test uploadProfilePicture with user having both LOCAL and GOOGLE auth (multiple auth methods)
     */
    @Test
    void whenUploadProfilePictureWithMultipleAuthMethods_thenSuccessfullyUploads() throws IOException {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "John", "Doe", true);
        MultipartFile file = mock(MultipartFile.class);

        // User has both LOCAL (verified) and GOOGLE auth
        UserAuthentication localAuth = UserAuthentication.createLocalAuth(user, "john@example.com", "hashedPassword");
        localAuth.setEnabled(true);
        UserAuthentication googleAuth = UserAuthentication.createOAuthAuth(user, AuthenticationType.GOOGLE, "google-uid-123", "john@gmail.com");

        ProcessedImage processedImage = new ProcessedImage(
                new ByteArrayInputStream(new byte[100]),
                100L,
                "image/png"
        );

        String expectedUrl = "https://bucket.s3.region.amazonaws.com/profile-pictures/" + userId + "/test.png";

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userAuthenticationRepository.findAllByUserId(userId)).thenReturn(List.of(localAuth, googleAuth));
        when(file.getOriginalFilename()).thenReturn("test.jpg");
        doNothing().when(imageProcessingService).validateFileSize(file);
        doNothing().when(imageProcessingService).validateContentType(file);
        doNothing().when(imageProcessingService).validateFileExtension(anyString());
        doNothing().when(imageProcessingService).validateMagicNumber(file);
        when(imageProcessingService.processImage(file)).thenReturn(processedImage);
        when(fileStorageService.uploadFile(anyString(), any(), anyString(), anyLong())).thenReturn(expectedUrl);
        when(userRepository.save(any(AppUser.class))).thenReturn(user);

        AppUser result = userService.uploadProfilePicture(userId, file);

        assertNotNull(result);
        assertEquals(expectedUrl, user.getProfilePictureUrl());
        verify(fileStorageService).uploadFile(anyString(), any(), eq("image/png"), eq(100L));
        verify(userRepository).save(user);
    }

    /**
     * Test uploadProfilePicture with empty profile picture URL (not null, but empty string)
     */
    @Test
    void whenUploadProfilePictureWithEmptyProfilePictureUrl_thenSuccessfullyUploads() throws IOException {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "John", "Doe", true);
        user.setProfilePictureUrl(""); // Empty string
        MultipartFile file = mock(MultipartFile.class);

        UserAuthentication googleAuth = UserAuthentication.createOAuthAuth(user, AuthenticationType.GOOGLE, "google-uid-123", "john@example.com");

        ProcessedImage processedImage = new ProcessedImage(
                new ByteArrayInputStream(new byte[100]),
                100L,
                "image/png"
        );

        String expectedUrl = "https://bucket.s3.region.amazonaws.com/profile-pictures/" + userId + "/test.png";

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userAuthenticationRepository.findAllByUserId(userId)).thenReturn(List.of(googleAuth));
        when(file.getOriginalFilename()).thenReturn("test.jpg");
        doNothing().when(imageProcessingService).validateFileSize(file);
        doNothing().when(imageProcessingService).validateContentType(file);
        doNothing().when(imageProcessingService).validateFileExtension(anyString());
        doNothing().when(imageProcessingService).validateMagicNumber(file);
        when(imageProcessingService.processImage(file)).thenReturn(processedImage);
        when(fileStorageService.uploadFile(anyString(), any(), anyString(), anyLong())).thenReturn(expectedUrl);
        when(userRepository.save(any(AppUser.class))).thenReturn(user);

        AppUser result = userService.uploadProfilePicture(userId, file);

        assertNotNull(result);
        assertEquals(expectedUrl, user.getProfilePictureUrl());
        // Verify extractKeyFromUrl was NOT called since URL is empty
        verify(fileStorageService, never()).extractKeyFromUrl(anyString());
        verify(fileStorageService, never()).deleteFile(anyString());
        verify(fileStorageService).uploadFile(anyString(), any(), eq("image/png"), eq(100L));
        verify(userRepository).save(user);
    }

    /**
     * Test uploadProfilePicture when extractKeyFromUrl returns null
     */
    @Test
    void whenUploadProfilePictureAndExtractKeyReturnsNull_thenSkipsDeletion() throws IOException {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, "John", "Doe", true);
        String oldPictureUrl = "https://invalid-url.com/some-path/image.jpg";
        user.setProfilePictureUrl(oldPictureUrl);
        MultipartFile file = mock(MultipartFile.class);

        UserAuthentication googleAuth = UserAuthentication.createOAuthAuth(user, AuthenticationType.GOOGLE, "google-uid-123", "john@example.com");

        ProcessedImage processedImage = new ProcessedImage(
                new ByteArrayInputStream(new byte[100]),
                100L,
                "image/png"
        );

        String expectedUrl = "https://bucket.s3.region.amazonaws.com/profile-pictures/" + userId + "/new.png";

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userAuthenticationRepository.findAllByUserId(userId)).thenReturn(List.of(googleAuth));
        when(file.getOriginalFilename()).thenReturn("test.jpg");
        doNothing().when(imageProcessingService).validateFileSize(file);
        doNothing().when(imageProcessingService).validateContentType(file);
        doNothing().when(imageProcessingService).validateFileExtension(anyString());
        doNothing().when(imageProcessingService).validateMagicNumber(file);
        when(imageProcessingService.processImage(file)).thenReturn(processedImage);
        when(fileStorageService.extractKeyFromUrl(oldPictureUrl)).thenReturn(null); // Returns null for invalid URL
        when(fileStorageService.uploadFile(anyString(), any(), anyString(), anyLong())).thenReturn(expectedUrl);
        when(userRepository.save(any(AppUser.class))).thenReturn(user);

        AppUser result = userService.uploadProfilePicture(userId, file);

        assertNotNull(result);
        assertEquals(expectedUrl, user.getProfilePictureUrl());
        verify(fileStorageService).extractKeyFromUrl(oldPictureUrl);
        // Verify deleteFile was NOT called since extractKeyFromUrl returned null
        verify(fileStorageService, never()).deleteFile(anyString());
        verify(fileStorageService).uploadFile(anyString(), any(), eq("image/png"), eq(100L));
        verify(userRepository).save(user);
    }
}
