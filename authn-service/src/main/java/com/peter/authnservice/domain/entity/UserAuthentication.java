package com.peter.authnservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"type", "email"})
})
public class UserAuthentication {
    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthenticationType type;

    private String providerId; // For OAuth providers, store the provider-specific user ID

    @Column(nullable = false)
    private String email;

    private String password;

    @Column(nullable = false)
    private boolean enabled = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private ZonedDateTime lastUpdatedAt;

    public UserAuthentication(AppUser user, AuthenticationType type, String providerId, String email, String password) {
        this.id = UUID.randomUUID(); // Generate a new UUID for the authentication
        this.user = user;
        this.type = type;
        this.email = email;
        if (type == AuthenticationType.LOCAL) {
            validatePasswordForLocalAuth(password);
            this.password = password;
            this.enabled = false;
        } else {
            this.providerId = providerId;
            this.password = null; // No password for non-local authentication types
            this.enabled = true;
        }
    }

    public static UserAuthentication createLocalAuth(AppUser user, String email, String password) {
        return new UserAuthentication(user, AuthenticationType.LOCAL, null, email, password);
    }

    public static UserAuthentication createOAuthAuth(AppUser user, AuthenticationType type, String providerId, String email) {
        if (type == AuthenticationType.LOCAL) {
            throw new IllegalArgumentException("Use createLocalAuth for local authentication.");
        }
        return new UserAuthentication(user, type, providerId, email, null);
    }

    /**
     * Validates password for local authentication
     * @param password the password to validate
     * @throws IllegalArgumentException if password is null or empty
     */
    private void validatePasswordForLocalAuth(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty for local authentication.");
        }
    }
}
