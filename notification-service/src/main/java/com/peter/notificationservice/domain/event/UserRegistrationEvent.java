package com.peter.notificationservice.domain.event;

public record UserRegistrationEvent(
        UserDetails userDetails,
        Email email) {
}
