package com.peter.notificationservice.domain.event;

public record UserDetails(
        String firstName,
        String lastName,
        String email) {
}
