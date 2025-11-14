package com.peter.authnservice;

import com.peter.authnservice.domain.entity.AppUser;
import com.peter.authnservice.domain.entity.AuthenticationType;
import com.peter.authnservice.domain.entity.UserAuthentication;
import com.peter.authnservice.repository.UserAuthenticationRepository;
import com.peter.authnservice.repository.UserRepository;
import com.peter.authnservice.service.FileStorageService;
import com.peter.authnservice.service.ImageProcessingService;
import com.peter.authnservice.util.JwtUtils;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ci")
public class ProfilePictureUploadIntegrationTest {

    private static final String UPLOAD_PROFILE_PICTURE_API_PATH = "/api/v1/users/me/profile-picture";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthenticationRepository userAuthenticationRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private Flyway flyway;

    @MockitoBean
    private FileStorageService fileStorageService;

    @MockitoBean
    private ImageProcessingService imageProcessingService;

    private AppUser testUser;
    private UserAuthentication testAuth;
    private String accessToken;

    @BeforeEach
    void setUp() {
        // Clean database before each test
        flyway.clean();
        flyway.migrate();

        // Create test user with verified GOOGLE auth
        testUser = new AppUser(UUID.randomUUID(), "John", "Doe", true);
        userRepository.save(testUser);

        testAuth = UserAuthentication.createOAuthAuth(
                testUser,
                AuthenticationType.GOOGLE,
                "google-123",
                "test@example.com"
        );
        userAuthenticationRepository.save(testAuth);

        // Generate access token
        accessToken = jwtUtils.generateAccessToken(testUser.getId());
    }

    /**
     * Test successful profile picture upload
     */
    @Test
    void whenUploadProfilePictureWithValidFile_thenReturns200AndUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        String s3Key = "profile-pictures/" + testUser.getId() + "/test.png";
        String expectedPresignedUrl = "https://bucket.s3.region.amazonaws.com/" + s3Key + "?X-Amz-Algorithm=AWS4-HMAC-SHA256";

        // Mock image processing service
        doNothing().when(imageProcessingService).validateFileSize(any());
        doNothing().when(imageProcessingService).validateContentType(any());
        doNothing().when(imageProcessingService).validateFileExtension(anyString());
        doNothing().when(imageProcessingService).validateMagicNumber(any());

        // Mock processImage to return a ProcessedImage
        com.peter.authnservice.domain.dto.ProcessedImage processedImage =
                new com.peter.authnservice.domain.dto.ProcessedImage(
                        new java.io.ByteArrayInputStream("processed".getBytes()),
                        100L,
                        "image/png"
                );
        when(imageProcessingService.processImage(any())).thenReturn(processedImage);

        // Mock file storage service
        when(fileStorageService.uploadFile(anyString(), any(), anyString(), anyLong())).thenReturn(s3Key);
        when(fileStorageService.generatePresignedUrl(eq(s3Key), any(Duration.class))).thenReturn(expectedPresignedUrl);

        mockMvc.perform(multipart(UPLOAD_PROFILE_PICTURE_API_PATH)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePictureUrl").value(expectedPresignedUrl));

        // Verify services were called
        verify(imageProcessingService, times(1)).processImage(any());
        verify(fileStorageService, times(1)).uploadFile(anyString(), any(), anyString(), anyLong());
    }

    /**
     * Test upload profile picture without authentication
     */
    @Test
    void whenUploadProfilePictureWithoutAuth_thenReturns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        mockMvc.perform(multipart(UPLOAD_PROFILE_PICTURE_API_PATH)
                        .file(file))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(fileStorageService);
    }

    /**
     * Test upload profile picture with invalid token
     */
    @Test
    void whenUploadProfilePictureWithInvalidToken_thenReturns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        mockMvc.perform(multipart(UPLOAD_PROFILE_PICTURE_API_PATH)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(fileStorageService);
    }

    /**
     * Test upload profile picture without file parameter
     */
    @Test
    void whenUploadProfilePictureWithoutFile_thenReturns400() throws Exception {
        mockMvc.perform(multipart(UPLOAD_PROFILE_PICTURE_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required request parameter 'file' is not present"));

        verifyNoInteractions(fileStorageService);
    }

    /**
     * Test upload profile picture with wrong Content-Type
     */
    @Test
    void whenUploadProfilePictureWithWrongContentType_thenReturns400() throws Exception {
        mockMvc.perform(multipart(UPLOAD_PROFILE_PICTURE_API_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Content-Type 'multipart/form-data' is required for file upload"));

        verifyNoInteractions(fileStorageService);
    }

    /**
     * Test upload profile picture with unverified LOCAL auth
     */
    @Test
    void whenUploadProfilePictureWithUnverifiedLocalAuth_thenReturns403() throws Exception {
        AppUser localUser = new AppUser(UUID.randomUUID(), "Jane", "Smith", true);
        userRepository.save(localUser);

        UserAuthentication localAuth = UserAuthentication.createLocalAuth(
                localUser,
                "local@example.com",
                "hashedPassword"
        );
        localAuth.setEnabled(false); // Unverified
        userAuthenticationRepository.save(localAuth);

        String localAccessToken = jwtUtils.generateAccessToken(localUser.getId());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        mockMvc.perform(multipart(UPLOAD_PROFILE_PICTURE_API_PATH)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + localAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Email verification required to upload profile picture."));

        verifyNoInteractions(fileStorageService);
    }

    /**
     * Test upload profile picture with verified LOCAL auth
     */
    @Test
    void whenUploadProfilePictureWithVerifiedLocalAuth_thenReturns200() throws Exception {
        AppUser localUser = new AppUser(UUID.randomUUID(), "Jane", "Smith", true);
        userRepository.save(localUser);

        UserAuthentication localAuth = UserAuthentication.createLocalAuth(
                localUser,
                "local@example.com",
                "hashedPassword"
        );
        localAuth.setEnabled(true); // Verified
        userAuthenticationRepository.save(localAuth);

        String localAccessToken = jwtUtils.generateAccessToken(localUser.getId());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        String s3Key = "profile-pictures/" + localUser.getId() + "/test.png";
        String expectedPresignedUrl = "https://bucket.s3.region.amazonaws.com/" + s3Key + "?X-Amz-Algorithm=AWS4-HMAC-SHA256";

        // Mock image processing service
        doNothing().when(imageProcessingService).validateFileSize(any());
        doNothing().when(imageProcessingService).validateContentType(any());
        doNothing().when(imageProcessingService).validateFileExtension(anyString());
        doNothing().when(imageProcessingService).validateMagicNumber(any());

        com.peter.authnservice.domain.dto.ProcessedImage processedImage =
                new com.peter.authnservice.domain.dto.ProcessedImage(
                        new java.io.ByteArrayInputStream("processed".getBytes()),
                        100L,
                        "image/png"
                );
        when(imageProcessingService.processImage(any())).thenReturn(processedImage);

        // Mock file storage service
        when(fileStorageService.uploadFile(anyString(), any(), anyString(), anyLong())).thenReturn(s3Key);
        when(fileStorageService.generatePresignedUrl(eq(s3Key), any(Duration.class))).thenReturn(expectedPresignedUrl);

        mockMvc.perform(multipart(UPLOAD_PROFILE_PICTURE_API_PATH)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + localAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePictureUrl").value(expectedPresignedUrl));

        verify(imageProcessingService, times(1)).processImage(any());
        verify(fileStorageService, times(1)).uploadFile(anyString(), any(), anyString(), anyLong());
    }

    /**
     * Test upload profile picture with disabled user account
     */
    @Test
    void whenUploadProfilePictureWithDisabledAccount_thenReturns403() throws Exception {
        // Disable user account
        testUser.setEnabled(false);
        userRepository.save(testUser);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        mockMvc.perform(multipart(UPLOAD_PROFILE_PICTURE_API_PATH)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("User account is disabled."));

        verifyNoInteractions(fileStorageService);
    }

    /**
     * Test upload profile picture with invalid file type
     */
    @Test
    void whenUploadProfilePictureWithInvalidFileType_thenReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "not an image".getBytes()
        );

        doThrow(new IllegalArgumentException("Invalid file type. Only JPEG, PNG, and WebP images are allowed"))
                .when(imageProcessingService).validateContentType(any());

        mockMvc.perform(multipart(UPLOAD_PROFILE_PICTURE_API_PATH)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid file type. Only JPEG, PNG, and WebP images are allowed"));

        verify(imageProcessingService, times(1)).validateContentType(any());
        verifyNoInteractions(fileStorageService);
    }

    /**
     * Test upload profile picture that is too large
     */
    @Test
    void whenUploadProfilePictureTooLarge_thenReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.jpg",
                "image/jpeg",
                new byte[10 * 1024 * 1024] // 10MB
        );

        doThrow(new IllegalArgumentException("File size exceeds maximum allowed size of 5 MB"))
                .when(imageProcessingService).validateFileSize(any());

        mockMvc.perform(multipart(UPLOAD_PROFILE_PICTURE_API_PATH)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File size exceeds maximum allowed size of 5 MB"));

        verify(imageProcessingService, times(1)).validateFileSize(any());
        verifyNoInteractions(fileStorageService);
    }

    /**
     * Test upload replaces existing profile picture
     */
    @Test
    void whenUploadProfilePictureWithExistingPicture_thenReplacesOldPicture() throws Exception {
        // Set existing profile picture
        String oldPictureUrl = "https://bucket.s3.region.amazonaws.com/profile-pictures/" + testUser.getId() + "/old.png";
        testUser.setProfilePictureUrl(oldPictureUrl);
        userRepository.save(testUser);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "new.jpg",
                "image/jpeg",
                "new image content".getBytes()
        );

        String oldKey = "profile-pictures/" + testUser.getId() + "/old.png";
        String newS3Key = "profile-pictures/" + testUser.getId() + "/new.png";
        String newPresignedUrl = "https://bucket.s3.region.amazonaws.com/" + newS3Key + "?X-Amz-Algorithm=AWS4-HMAC-SHA256";

        // Mock image processing service
        doNothing().when(imageProcessingService).validateFileSize(any());
        doNothing().when(imageProcessingService).validateContentType(any());
        doNothing().when(imageProcessingService).validateFileExtension(anyString());
        doNothing().when(imageProcessingService).validateMagicNumber(any());

        com.peter.authnservice.domain.dto.ProcessedImage processedImage =
                new com.peter.authnservice.domain.dto.ProcessedImage(
                        new java.io.ByteArrayInputStream("processed".getBytes()),
                        100L,
                        "image/png"
                );
        when(imageProcessingService.processImage(any())).thenReturn(processedImage);

        // Mock file storage service
        when(fileStorageService.extractKeyFromUrl(oldPictureUrl)).thenReturn(oldKey);
        doNothing().when(fileStorageService).deleteFile(oldKey);
        when(fileStorageService.uploadFile(anyString(), any(), anyString(), anyLong())).thenReturn(newS3Key);
        when(fileStorageService.generatePresignedUrl(eq(newS3Key), any(Duration.class))).thenReturn(newPresignedUrl);

        mockMvc.perform(multipart(UPLOAD_PROFILE_PICTURE_API_PATH)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePictureUrl").value(newPresignedUrl));

        // Verify services were called
        verify(imageProcessingService, times(1)).processImage(any());
        verify(fileStorageService, times(1)).extractKeyFromUrl(oldPictureUrl);
        verify(fileStorageService, times(1)).deleteFile(oldKey);
        verify(fileStorageService, times(1)).uploadFile(anyString(), any(), anyString(), anyLong());
    }
}
