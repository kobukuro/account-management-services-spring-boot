package com.peter.notificationservice.domain.event;

public record Event(
        UserDetails userDetails,
        Email email) {
}
