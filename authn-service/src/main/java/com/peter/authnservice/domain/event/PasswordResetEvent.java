package com.peter.authnservice.domain.event;

public record PasswordResetEvent(
        UserDetails userDetails,
        Email email) implements DomainEvent {
}
