package com.peter.authnservice.domain.event;

public record UserDetails(
        String firstName,
        String lastName,
        String email) {
}
