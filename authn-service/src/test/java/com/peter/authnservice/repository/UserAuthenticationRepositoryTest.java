package com.peter.authnservice.repository;

import com.peter.authnservice.domain.entity.AppUser;
import com.peter.authnservice.domain.entity.AuthenticationType;
import com.peter.authnservice.domain.entity.UserAuthentication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository layer tests for UserAuthenticationRepository
 * Tests custom query methods for finding user authentications
 */
@DataJpaTest
@ActiveProfiles("ci")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserAuthenticationRepositoryTest {

    @Autowired
    private UserAuthenticationRepository userAuthenticationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Flyway flyway;

    private AppUser testUser;
    private final String testEmail = "test@example.com";
    private final String testProviderId = "google_123456";

    @BeforeEach
    void setUp() {
        // Reset database before each test
        flyway.clean();
        flyway.migrate();

        // Create test user
        testUser = new AppUser("John", "Doe", true);
        testUser = userRepository.save(testUser);
    }

    // ==================== FIND BY EMAIL AND TYPE TESTS ====================

    /**
     * Test finding LOCAL authentication by email
     */
    @Test
    void whenFindByEmailAndTypeLocal_thenReturnsAuthentication() {
        UserAuthentication localAuth = UserAuthentication.createLocalAuth(
                testUser, testEmail, "hashedPassword");
        userAuthenticationRepository.save(localAuth);

        Optional<UserAuthentication> found = userAuthenticationRepository
                .findByEmailAndType(testEmail, AuthenticationType.LOCAL);

        assertTrue(found.isPresent());
        assertEquals(testEmail, found.get().getEmail());
        assertEquals(AuthenticationType.LOCAL, found.get().getType());
        assertEquals(testUser.getId(), found.get().getUser().getId());
    }

    /**
     * Test finding GOOGLE authentication by email
     */
    @Test
    void whenFindByEmailAndTypeGoogle_thenReturnsAuthentication() {
        UserAuthentication googleAuth = UserAuthentication.createOAuthAuth(
                testUser, AuthenticationType.GOOGLE, testProviderId, testEmail);
        userAuthenticationRepository.save(googleAuth);

        Optional<UserAuthentication> found = userAuthenticationRepository
                .findByEmailAndType(testEmail, AuthenticationType.GOOGLE);

        assertTrue(found.isPresent());
        assertEquals(testEmail, found.get().getEmail());
        assertEquals(AuthenticationType.GOOGLE, found.get().getType());
        assertEquals(testProviderId, found.get().getProviderId());
        assertEquals(testUser.getId(), found.get().getUser().getId());
    }

    /**
     * Test finding authentication with non-existent email
     */
    @Test
    void whenFindByNonExistentEmail_thenReturnsEmpty() {
        Optional<UserAuthentication> found = userAuthenticationRepository
                .findByEmailAndType("nonexistent@example.com", AuthenticationType.LOCAL);

        assertFalse(found.isPresent());
    }

    /**
     * Test finding authentication with wrong type
     */
    @Test
    void whenFindByEmailWithWrongType_thenReturnsEmpty() {
        UserAuthentication localAuth = UserAuthentication.createLocalAuth(
                testUser, testEmail, "hashedPassword");
        userAuthenticationRepository.save(localAuth);

        Optional<UserAuthentication> found = userAuthenticationRepository
                .findByEmailAndType(testEmail, AuthenticationType.GOOGLE);

        assertFalse(found.isPresent());
    }

    /**
     * Test that same email can exist with different authentication types
     */
    @Test
    void whenSameEmailDifferentTypes_thenBothExist() {
        UserAuthentication localAuth = UserAuthentication.createLocalAuth(
                testUser, testEmail, "hashedPassword");
        UserAuthentication googleAuth = UserAuthentication.createOAuthAuth(
                testUser, AuthenticationType.GOOGLE, testProviderId, testEmail);

        userAuthenticationRepository.save(localAuth);
        userAuthenticationRepository.save(googleAuth);

        Optional<UserAuthentication> foundLocal = userAuthenticationRepository
                .findByEmailAndType(testEmail, AuthenticationType.LOCAL);
        Optional<UserAuthentication> foundGoogle = userAuthenticationRepository
                .findByEmailAndType(testEmail, AuthenticationType.GOOGLE);

        assertTrue(foundLocal.isPresent());
        assertTrue(foundGoogle.isPresent());
        assertEquals(AuthenticationType.LOCAL, foundLocal.get().getType());
        assertEquals(AuthenticationType.GOOGLE, foundGoogle.get().getType());
    }

    // ==================== FIND BY USER ID AND TYPE TESTS ====================

    /**
     * Test finding authentication by user ID and type
     */
    @Test
    void whenFindByUserIdAndType_thenReturnsAuthentication() {
        UserAuthentication localAuth = UserAuthentication.createLocalAuth(
                testUser, testEmail, "hashedPassword");
        userAuthenticationRepository.save(localAuth);

        Optional<UserAuthentication> found = userAuthenticationRepository
                .findByUserIdAndType(testUser.getId(), AuthenticationType.LOCAL);

        assertTrue(found.isPresent());
        assertEquals(testUser.getId(), found.get().getUser().getId());
        assertEquals(AuthenticationType.LOCAL, found.get().getType());
    }

    /**
     * Test finding authentication with non-existent user ID
     */
    @Test
    void whenFindByNonExistentUserId_thenReturnsEmpty() {
        UUID nonExistentUserId = UUID.randomUUID();

        Optional<UserAuthentication> found = userAuthenticationRepository
                .findByUserIdAndType(nonExistentUserId, AuthenticationType.LOCAL);

        assertFalse(found.isPresent());
    }

    /**
     * Test finding authentication by user ID with wrong type
     */
    @Test
    void whenFindByUserIdWithWrongType_thenReturnsEmpty() {
        UserAuthentication localAuth = UserAuthentication.createLocalAuth(
                testUser, testEmail, "hashedPassword");
        userAuthenticationRepository.save(localAuth);

        Optional<UserAuthentication> found = userAuthenticationRepository
                .findByUserIdAndType(testUser.getId(), AuthenticationType.GOOGLE);

        assertFalse(found.isPresent());
    }

    /**
     * Test that same user can have multiple authentication types
     */
    @Test
    void whenUserHasMultipleAuthTypes_thenBothFound() {
        UserAuthentication localAuth = UserAuthentication.createLocalAuth(
                testUser, testEmail, "hashedPassword");
        UserAuthentication googleAuth = UserAuthentication.createOAuthAuth(
                testUser, AuthenticationType.GOOGLE, testProviderId, "test@gmail.com");

        userAuthenticationRepository.save(localAuth);
        userAuthenticationRepository.save(googleAuth);

        Optional<UserAuthentication> foundLocal = userAuthenticationRepository
                .findByUserIdAndType(testUser.getId(), AuthenticationType.LOCAL);
        Optional<UserAuthentication> foundGoogle = userAuthenticationRepository
                .findByUserIdAndType(testUser.getId(), AuthenticationType.GOOGLE);

        assertTrue(foundLocal.isPresent());
        assertTrue(foundGoogle.isPresent());
        assertEquals(testUser.getId(), foundLocal.get().getUser().getId());
        assertEquals(testUser.getId(), foundGoogle.get().getUser().getId());
    }

    // ==================== FIND BY PROVIDER ID AND TYPE TESTS ====================

    /**
     * Test finding authentication by provider ID and type
     */
    @Test
    void whenFindByProviderIdAndType_thenReturnsAuthentication() {
        UserAuthentication googleAuth = UserAuthentication.createOAuthAuth(
                testUser, AuthenticationType.GOOGLE, testProviderId, testEmail);
        userAuthenticationRepository.save(googleAuth);

        Optional<UserAuthentication> found = userAuthenticationRepository
                .findByProviderIdAndType(testProviderId, AuthenticationType.GOOGLE);

        assertTrue(found.isPresent());
        assertEquals(testProviderId, found.get().getProviderId());
        assertEquals(AuthenticationType.GOOGLE, found.get().getType());
        assertEquals(testUser.getId(), found.get().getUser().getId());
    }

    /**
     * Test finding authentication with non-existent provider ID
     */
    @Test
    void whenFindByNonExistentProviderId_thenReturnsEmpty() {
        Optional<UserAuthentication> found = userAuthenticationRepository
                .findByProviderIdAndType("nonexistent_provider_123", AuthenticationType.GOOGLE);

        assertFalse(found.isPresent());
    }

    /**
     * Test finding authentication by provider ID with wrong type
     */
    @Test
    void whenFindByProviderIdWithWrongType_thenReturnsEmpty() {
        UserAuthentication googleAuth = UserAuthentication.createOAuthAuth(
                testUser, AuthenticationType.GOOGLE, testProviderId, testEmail);
        userAuthenticationRepository.save(googleAuth);

        Optional<UserAuthentication> found = userAuthenticationRepository
                .findByProviderIdAndType(testProviderId, AuthenticationType.LOCAL);

        assertFalse(found.isPresent());
    }

    /**
     * Test that provider ID is null for LOCAL authentication
     */
    @Test
    void whenLocalAuth_thenProviderIdIsNull() {
        UserAuthentication localAuth = UserAuthentication.createLocalAuth(
                testUser, testEmail, "hashedPassword");
        userAuthenticationRepository.save(localAuth);

        Optional<UserAuthentication> found = userAuthenticationRepository
                .findByEmailAndType(testEmail, AuthenticationType.LOCAL);

        assertTrue(found.isPresent());
        assertNull(found.get().getProviderId());
    }

    // ==================== CRUD OPERATIONS TESTS ====================

    /**
     * Test saving and retrieving authentication
     */
    @Test
    void whenSaveAuthentication_thenCanRetrieve() {
        UserAuthentication auth = UserAuthentication.createLocalAuth(
                testUser, testEmail, "hashedPassword");
        UserAuthentication savedAuth = userAuthenticationRepository.save(auth);

        assertNotNull(savedAuth.getId());

        Optional<UserAuthentication> retrieved = userAuthenticationRepository.findById(savedAuth.getId());
        assertTrue(retrieved.isPresent());
        assertEquals(savedAuth.getId(), retrieved.get().getId());
    }

    /**
     * Test deleting authentication
     */
    @Test
    void whenDeleteAuthentication_thenNotFound() {
        UserAuthentication auth = UserAuthentication.createLocalAuth(
                testUser, testEmail, "hashedPassword");
        UserAuthentication savedAuth = userAuthenticationRepository.save(auth);

        userAuthenticationRepository.delete(savedAuth);

        Optional<UserAuthentication> found = userAuthenticationRepository.findById(savedAuth.getId());
        assertFalse(found.isPresent());
    }

    /**
     * Test updating authentication email
     */
    @Test
    void whenUpdateAuthenticationEmail_thenEmailUpdated() {
        UserAuthentication auth = UserAuthentication.createOAuthAuth(
                testUser, AuthenticationType.GOOGLE, testProviderId, "old@example.com");
        UserAuthentication savedAuth = userAuthenticationRepository.save(auth);

        savedAuth.setEmail("new@example.com");
        userAuthenticationRepository.save(savedAuth);

        Optional<UserAuthentication> updated = userAuthenticationRepository
                .findByProviderIdAndType(testProviderId, AuthenticationType.GOOGLE);

        assertTrue(updated.isPresent());
        assertEquals("new@example.com", updated.get().getEmail());
    }
}
