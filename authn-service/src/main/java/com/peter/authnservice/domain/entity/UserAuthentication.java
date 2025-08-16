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

    @Column(nullable = false)
    private String email;

    private String password;

    @Column(nullable = false)
    private Boolean enabled = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private ZonedDateTime lastUpdatedAt;

    public UserAuthentication(AppUser user, AuthenticationType type, String email, String password) {
        this.id = UUID.randomUUID(); // Generate a new UUID for the authentication
        this.user = user;
        this.type = type;
        this.email = email;
        if (type == AuthenticationType.LOCAL) {
            if (password == null || password.trim().isEmpty()) {
                throw new IllegalArgumentException("Password cannot be null or empty for local authentication.");
            }
            this.password = password;
            this.enabled = false;
        } else {
            this.password = null; // No password for non-local authentication types
            this.enabled = true;
        }
    }

    public static UserAuthentication createLocalAuth(AppUser user, String email, String password) {
        return new UserAuthentication(user, AuthenticationType.LOCAL, email, password);
    }

}
