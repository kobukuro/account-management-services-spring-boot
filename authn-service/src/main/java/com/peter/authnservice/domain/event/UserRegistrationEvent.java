package com.peter.authnservice.domain.event;

public record UserRegistrationEvent(
        UserDetails userDetails,
        Email email) {
}
