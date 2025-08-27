package com.peter.authnservice.repository;

import com.peter.authnservice.domain.entity.AuthenticationType;
import com.peter.authnservice.domain.entity.UserAuthentication;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAuthenticationRepository extends CrudRepository<UserAuthentication, UUID> {
    Optional<UserAuthentication> findByUserIdAndType(UUID userId, AuthenticationType type);
    Optional<UserAuthentication> findByEmailAndType(String email, AuthenticationType type);
    Optional<UserAuthentication> findByProviderIdAndType(String providerId, AuthenticationType type);
}
