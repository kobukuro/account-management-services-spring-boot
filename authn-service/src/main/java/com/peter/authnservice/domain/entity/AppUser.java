package com.peter.authnservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
public class AppUser implements UserDetails {
    @Id
    private UUID id;
    private String firstName;
    private String lastName;
    private String profilePictureUrl;

    @Column(nullable = false)
    private Boolean enabled = false;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserAuthentication> authentications = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private ZonedDateTime lastUpdatedAt;

    public AppUser(UUID id, String firstName, String lastName, Boolean enabled) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.enabled = enabled;
    }

    public AppUser(String firstName, String lastName, Boolean enabled) {
        this.id = UUID.randomUUID(); // Generate a new UUID for the user
        this.firstName = firstName;
        this.lastName = lastName;
        this.enabled = enabled;
    }

    @Override
    public String getUsername() {
        return null;
    }

    @Override
    public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
        return new ArrayList<>();
    }

    @Override
    public String getPassword() {
        return authentications.stream()
                .filter(auth -> auth.getType() == AuthenticationType.LOCAL && auth.getEnabled())
                .findFirst()
                .map(UserAuthentication::getPassword)
                .orElse(null);
    }
}
