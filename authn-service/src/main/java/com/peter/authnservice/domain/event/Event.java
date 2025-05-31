package com.peter.authnservice.domain.event;

public record Event(
        UserDetails userDetails,
        Email email) {
}
