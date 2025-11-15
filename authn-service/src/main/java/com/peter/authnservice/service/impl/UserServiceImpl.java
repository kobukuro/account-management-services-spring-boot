package com.peter.authnservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peter.authnservice.domain.dto.ProcessedImage;
import com.peter.authnservice.domain.dto.TokenPair;
import com.peter.authnservice.domain.dto.oauth.GoogleUserInfo;
import com.peter.authnservice.domain.entity.AppUser;
import com.peter.authnservice.domain.entity.AuthenticationType;
import com.peter.authnservice.domain.entity.UserAuthentication;
import com.peter.authnservice.domain.event.*;
import com.peter.authnservice.exception.*;
import com.peter.authnservice.repository.UserAuthenticationRepository;
import com.peter.authnservice.repository.UserRepository;
import com.peter.authnservice.service.ImageProcessingService;
import com.peter.authnservice.service.UserService;
import com.peter.authnservice.service.FileStorageService;
import com.peter.authnservice.util.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.peter.authnservice.domain.entity.UserAuthentication.createLocalAuth;
import static com.peter.authnservice.domain.entity.UserAuthentication.createOAuthAuth;

@Service
@Transactional // Utilize Spring's transaction management to roll back the transaction if an exception occurs
public class UserServiceImpl implements UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    private final UserRepository userRepository;
    private final UserAuthenticationRepository userAuthenticationRepository;
    private final JwtUtils jwtUtils;
    private final FileStorageService fileStorageService;
    private final ImageProcessingService imageProcessingService;
    @Value("${app-name}")
    private String appName;
    @Value("${jwt.verification.expiration}")
    private long verificationTokenExpirationInMilliseconds;
    @Value("${jwt.reset-password.expiration}")
    private long resetPasswordTokenExpirationInMilliseconds;
    @Value("${frontend-url}")
    private String frontendUrl;
    @Value("${file.storage.presigned-url-expiration-hours:24}")
    private int presignedUrlExpirationHours;
    private final KafkaTemplate<String, Event> kafkaTemplate;

    private final RestTemplate restTemplate;
    @Value("${google.oauth2.client-id}")
    private String googleClientId;
    @Value("${google.oauth2.client-secret}")
    private String googleClientSecret;

    public UserServiceImpl(UserRepository userRepository,
                           UserAuthenticationRepository userAuthenticationRepository,
                           JwtUtils jwtUtils,
                           KafkaTemplate<String, Event> kafkaTemplate,
                           RestTemplate restTemplate,
                           FileStorageService fileStorageService,
                           ImageProcessingService imageProcessingService) {
        this.userRepository = userRepository;
        this.userAuthenticationRepository = userAuthenticationRepository;
        this.jwtUtils = jwtUtils;
        this.kafkaTemplate = kafkaTemplate;
        this.restTemplate = restTemplate;
        this.fileStorageService = fileStorageService;
        this.imageProcessingService = imageProcessingService;
    }

    @Override
    public AppUser register(String firstName, String lastName, String email, String password) {
        if (userAuthenticationRepository.findByEmailAndType(email, AuthenticationType.LOCAL).isPresent()) {
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
        AppUser newUser = new AppUser(userId, firstName, lastName, true);
        userRepository.save(newUser);
        UserAuthentication userAuth = createLocalAuth(newUser, email, hashedPassword);
        userAuthenticationRepository.save(userAuth);

        return newUser;
    }

    @Override
    public void activateAccount(String token) {
        if (!jwtUtils.validateToken(token)) {
            throw new TokenNotValidException("Invalid or expired verification token");
        }
        UUID userId = jwtUtils.getUserIdFromToken(token);

        UserAuthentication userAuth = userAuthenticationRepository.findByUserIdAndType(userId, AuthenticationType.LOCAL)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (userAuth.getEnabled()) {
            throw new UserAlreadyVerifiedException("This user has already been verified.");
        }
        userAuth.setEnabled(true);
        userAuthenticationRepository.save(userAuth);
    }

    @Override
    public void resendActivationEmail(String email) {
        UserAuthentication userAuth = userAuthenticationRepository.findByEmailAndType(email, AuthenticationType.LOCAL)
                .orElseThrow(() -> new EmailNotFoundException("Email not found"));
        AppUser user = userAuth.getUser();
        if (!user.getEnabled()) {
            throw new UserAccountDisabledException("User account is disabled.");
        }
        if (userAuth.getEnabled()) {
            throw new UserAlreadyVerifiedException("This user has already been verified.");
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
        UserAuthentication userAuth = userAuthenticationRepository.findByEmailAndType(email, AuthenticationType.LOCAL)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

        AppUser user = userAuth.getUser();
        if (!user.getEnabled()) {
            throw new UserAccountDisabledException("User account is disabled.");
        }

        if (!userAuth.getEnabled()) {
            throw new EmailNotVerifiedException("This email has not been verified.\nPlease check your email for the verification link.");
        }
        if (!BCrypt.checkpw(password, userAuth.getPassword())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        UUID userId = user.getId();
        String accessToken = jwtUtils.generateAccessToken(userId);
        String refreshToken = jwtUtils.generateRefreshToken(userId);
        return new TokenPair(accessToken, refreshToken);
    }

    @Override
    public void resetPassword(String email) {
        UserAuthentication userAuth = userAuthenticationRepository.findByEmailAndType(email, AuthenticationType.LOCAL)
                .orElseThrow(() -> new EmailNotFoundException("Email not found"));
        AppUser user = userAuth.getUser();
        if (!user.getEnabled()) {
            throw new UserAccountDisabledException("User account is disabled.");
        }
        if (!userAuth.getEnabled()) {
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

        if (!user.getEnabled()) {
            throw new UserAccountDisabledException("User account is disabled.");
        }

        UserAuthentication userAuth = userAuthenticationRepository.findByUserIdAndType(userId, AuthenticationType.LOCAL)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!userAuth.getEnabled()) {
            throw new EmailNotVerifiedException("This email has not been verified.\nPlease check your email for the verification link.");
        }
        String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        userAuth.setPassword(hashedPassword);
        userAuthenticationRepository.save(userAuth);
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        String email = userAuth.getEmail();
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

        if (!user.getEnabled()) {
            throw new UserAccountDisabledException("User account is disabled.");
        }

        UserAuthentication userAuth = userAuthenticationRepository.findByUserIdAndType(userId, AuthenticationType.LOCAL)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!userAuth.getEnabled()) {
            throw new EmailNotVerifiedException("This email has not been verified.\nPlease check your email for the verification link.");
        }

        if (!BCrypt.checkpw(currentPassword, userAuth.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }
        String hashedNewPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        userAuth.setPassword(hashedNewPassword);
        userAuthenticationRepository.save(userAuth);
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        String email = userAuth.getEmail();
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

        if (!user.getEnabled()) {
            throw new UserAccountDisabledException("User account is disabled.");
        }

        return jwtUtils.generateAccessToken(userId);
    }


    @Override
    public TokenPair googleOAuthLogin(String authorizationCode, String redirectUri) {
        try {
            String decodedAuthCode = URLDecoder.decode(authorizationCode, StandardCharsets.UTF_8);
            String googleAccessToken = exchangeCodeForAccessToken(decodedAuthCode, redirectUri);
            GoogleUserInfo googleUserInfo = getUserInfoFromGoogle(googleAccessToken);
            Optional<UserAuthentication> existingGoogleAuth = userAuthenticationRepository
                    .findByProviderIdAndType(googleUserInfo.getId(), AuthenticationType.GOOGLE);
            UserAuthentication googleAuth;
            if (existingGoogleAuth.isPresent()) {
                googleAuth = existingGoogleAuth.get();
                String latestEmail = googleUserInfo.getEmail();
                // Update email if it has changed
                if (!googleAuth.getEmail().equals(latestEmail)) {
                    googleAuth.setEmail(latestEmail);
                    userAuthenticationRepository.save(googleAuth);
                }
            } else {
                UUID userId = UUID.randomUUID();
                String firstName = googleUserInfo.getGivenName();
                String lastName = googleUserInfo.getFamilyName();
                AppUser newUser = new AppUser(userId, firstName, lastName, true);
                userRepository.save(newUser);
                googleAuth = createOAuthAuth(newUser, AuthenticationType.GOOGLE, googleUserInfo.getId(), googleUserInfo.getEmail());
                userAuthenticationRepository.save(googleAuth);
            }
            UUID userId = googleAuth.getUser().getId();
            String accessToken = jwtUtils.generateAccessToken(userId);
            String refreshToken = jwtUtils.generateRefreshToken(userId);
            return new TokenPair(accessToken, refreshToken);
        } catch (InvalidAuthorizationCodeException | MismatchRedirectUriException e) {
            throw e; // Rethrow the custom exception
        } catch (Exception e) {
            throw new OAuthException("Google OAuth login failed due to unexpected error");
        }
    }

    private String exchangeCodeForAccessToken(String authorizationCode, String redirectUri) throws JsonProcessingException {
        String tokenUrl = "https://oauth2.googleapis.com/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", googleClientId);
        params.add("client_secret", googleClientSecret);
        params.add("code", authorizationCode);
        params.add("grant_type", "authorization_code");
        params.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<>() {
                    }
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                String responseBody = e.getResponseBodyAsString();
                ObjectMapper mapper = new ObjectMapper();
                JsonNode jsonNode = mapper.readTree(responseBody);

                if (jsonNode.has("error")) {
                    String errorValue = jsonNode.get("error").asText();
                    if (errorValue.equals("invalid_grant")) {
                        throw new InvalidAuthorizationCodeException("The authorization code is malformed, invalid or has already been used.");
                    }
                    if (errorValue.equals("redirect_uri_mismatch")) {
                        throw new MismatchRedirectUriException("The redirect URI provided does not match the ones registered.");
                    }
                }
            }
        }
        throw new OAuthException("Failed to exchange authorization code for access token. The response from Google was not successful or did not contain an access token.");
    }

    private GoogleUserInfo getUserInfoFromGoogle(String accessToken) {
        String userInfoUrl = "https://www.googleapis.com/oauth2/v2/userinfo";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    userInfoUrl,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {
                    }
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> userInfo = response.getBody();
                return new GoogleUserInfo(
                        (String) userInfo.get("id"),
                        (String) userInfo.get("email"),
                        (Boolean) userInfo.get("verified_email"),
                        (String) userInfo.get("name"),
                        (String) userInfo.get("given_name"),
                        (String) userInfo.get("family_name"),
                        (String) userInfo.get("picture")
                );
            }
            throw new OAuthException("OAuth user info retrieval failed: Google API did not return a successful response.");
        } catch (Exception e) {
            throw new OAuthException("OAuth user info retrieval failed: " + e.getMessage());
        }
    }

    @Override
    public AppUser updateProfile(UUID userId, String firstName, String lastName) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!user.getEnabled()) {
            throw new UserAccountDisabledException("User account is disabled.");
        }

        boolean updated = false;

        if (firstName != null && !firstName.isBlank()) {
            user.setFirstName(firstName);
            updated = true;
        }

        if (lastName != null && !lastName.isBlank()) {
            user.setLastName(lastName);
            updated = true;
        }

        if (!updated) {
            throw new IllegalArgumentException("At least one field (firstName or lastName) must be provided");
        }

        return userRepository.save(user);
    }

    /**
     * Upload profile picture for a user.
     * This method is intentionally NOT annotated with @Transactional to handle
     * the transaction boundaries explicitly. This allows proper handling of
     * S3 operations (which cannot be rolled back) and database operations.
     * <p>
     * Flow:
     * 1. Validate user and permissions (read-only transaction)
     * 2. Validate and process image file (no DB/S3 operations)
     * 3. Upload to S3 first (easier to clean up if DB fails)
     * 4. Update database in separate transaction (atomic)
     * 5. If DB fails, compensate by deleting the uploaded S3 file
     * 6. If DB succeeds, best-effort cleanup of old S3 file
     */
    @Override
    public AppUser uploadProfilePicture(UUID userId, MultipartFile file) {
        // Step 1: Validate user and permissions in read-only transaction
        validateUserForProfilePictureUpload(userId);
        String oldProfilePictureUrl = getOldProfilePictureUrl(userId);

        try {
            // Step 2: Validate and process file (no DB or S3 operations)
            // Read file bytes once to avoid stream exhaustion
            byte[] fileBytes = file.getBytes();
            
            imageProcessingService.validateFileSize(file);
            imageProcessingService.validateContentType(file);
            imageProcessingService.validateFileExtension(file.getOriginalFilename());
            imageProcessingService.validateMagicNumber(fileBytes);

            ProcessedImage processedImage = imageProcessingService.processImage(fileBytes);

            // Step 3: Upload to S3 first (before DB update)
            String fileExtension = ".png"; // We're converting to PNG
            String fileName = UUID.randomUUID() + fileExtension;
            String s3Key = String.format("profile-pictures/%s/%s", userId, fileName);

            // Upload returns the S3 key (not a public URL - file is private)
            String uploadedKey = fileStorageService.uploadFile(
                    s3Key,
                    processedImage.inputStream(),
                    processedImage.contentType(),
                    processedImage.size()
            );

            // Step 4: Update database in separate transaction
            // Store the S3 key in the database (not a URL)
            AppUser updatedUser;
            try {
                updatedUser = updateUserProfilePictureUrlInTransaction(userId, uploadedKey);
            } catch (Exception dbException) {
                // Step 5: Compensate - Delete the newly uploaded S3 file if DB update fails
                logger.error("Database update failed for user {}, initiating S3 cleanup compensation", userId);
                try {
                    fileStorageService.deleteFile(s3Key);
                    logger.info("Successfully deleted S3 file {} after database failure", s3Key);
                } catch (Exception s3CleanupException) {
                    logger.error("Failed to clean up S3 file {} after database failure for user {}: {}",
                            s3Key, userId, s3CleanupException.getMessage(), s3CleanupException);
                }
                throw new FileUploadException("Failed to save profile picture URL to database", dbException);
            }

            // Step 6: Best-effort cleanup of old file (after successful DB update)
            deleteOldProfilePicture(oldProfilePictureUrl, userId);

            // Step 7: Generate presigned URL for the response (temporary access)
            // This URL will expire after the configured duration (e.g., 24 hours)
            String presignedUrl = fileStorageService.generatePresignedUrl(
                    uploadedKey,
                    Duration.ofHours(presignedUrlExpirationHours)
            );

            // Set the presigned URL in the response object for the client
            // Note: The database stores the S3 key, but we return a presigned URL to the client
            updatedUser.setProfilePictureUrl(presignedUrl);

            return updatedUser;

        } catch (IOException e) {
            throw new FileUploadException("Failed to process image file", e);
        } catch (IllegalArgumentException e) {
            throw e; // Re-throw validation exceptions
        } catch (FileUploadException e) {
            throw e; // Re-throw file upload exceptions
        } catch (Exception e) {
            throw new FileUploadException("Failed to upload profile picture: " + e.getMessage(), e);
        }
    }

    /**
     * Validate user exists, is enabled, and has verified email if using LOCAL auth.
     * This runs in a read-only transaction.
     */
    @Transactional(readOnly = true)
    protected void validateUserForProfilePictureUpload(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!user.getEnabled()) {
            throw new UserAccountDisabledException("User account is disabled.");
        }

        // Check if user only has LOCAL authentication and it's not verified
        var userAuths = userAuthenticationRepository.findAllByUserId(userId);
        boolean hasOnlyLocalAuth = userAuths.size() == 1 &&
                                    userAuths.getFirst().getType() == AuthenticationType.LOCAL;

        if (hasOnlyLocalAuth && !userAuths.getFirst().getEnabled()) {
            throw new EmailNotVerifiedException("Email verification required to upload profile picture.");
        }
    }

    /**
     * Get the current profile picture URL for cleanup purposes.
     * This runs in a read-only transaction.
     */
    @Transactional(readOnly = true)
    protected String getOldProfilePictureUrl(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return user.getProfilePictureUrl();
    }

    /**
     * Update user's profile picture S3 key in the database.
     * This runs in a NEW transaction to ensure atomicity and avoid lost updates.
     * The user is re-fetched within this transaction to ensure we're working with
     * the latest state and avoid overwriting concurrent updates to other fields.
     *
     * @param userId the user ID
     * @param s3Key the S3 object key (not a presigned URL) to store in the database
     * @return the updated user entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected AppUser updateUserProfilePictureUrlInTransaction(UUID userId, String s3Key) {
        // Re-fetch user in this new transaction to avoid lost updates
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        // Store the S3 key (not a presigned URL) in the database
        // Presigned URLs are generated on-demand when needed
        user.setProfilePictureUrl(s3Key);
        return userRepository.save(user);
    }

    /**
     * Delete old profile picture from S3 storage.
     * This is a best-effort operation - failures are logged but don't fail the upload.
     */
    protected void deleteOldProfilePicture(String oldProfilePictureUrl, UUID userId) {
        if (oldProfilePictureUrl != null && !oldProfilePictureUrl.isEmpty()) {
            String oldKey = fileStorageService.extractKeyFromUrl(oldProfilePictureUrl);
            if (oldKey != null) {
                try {
                    fileStorageService.deleteFile(oldKey);
                    logger.info("Successfully deleted old profile picture {} for user {}", oldKey, userId);
                } catch (Exception e) {
                    // Log but don't fail if old file deletion fails
                    logger.warn("Failed to delete old profile picture {} for user {}: {}",
                            oldKey, userId, e.getMessage(), e);
                }
            }
        }
    }
}
