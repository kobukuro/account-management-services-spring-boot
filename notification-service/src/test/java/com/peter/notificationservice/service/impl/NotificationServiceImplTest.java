package com.peter.notificationservice.service.impl;

import com.peter.notificationservice.domain.event.Email;
import com.peter.notificationservice.domain.event.Event;
import com.peter.notificationservice.domain.event.UserDetails;
import com.peter.notificationservice.service.notification.NotificationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationServiceImpl.
 * Tests the strategy pattern implementation for processing notification events.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationStrategy emailStrategy;

    @Mock
    private NotificationStrategy smsStrategy;

    private NotificationServiceImpl notificationService;

    private Event createTestEvent() {
        UserDetails userDetails = new UserDetails("John", "Doe", "john.doe@example.com");
        Email email = new Email(
                "Test Subject",
                "test-template",
                Map.of("key", "value")
        );
        return new Event(userDetails, email);
    }

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(List.of(emailStrategy, smsStrategy));
    }

    @Test
    void shouldProcessEventWithMatchingStrategy() {
        // Given
        Event event = createTestEvent();
        when(emailStrategy.canHandle(event)).thenReturn(true);
        when(smsStrategy.canHandle(event)).thenReturn(false);

        // When
        notificationService.processNotification(event);

        // Then
        verify(emailStrategy).canHandle(event);
        verify(emailStrategy).notify(event);
        verify(smsStrategy).canHandle(event);
        verify(smsStrategy, never()).notify(event);
    }

    @Test
    void shouldProcessEventWithMultipleMatchingStrategies() {
        // Given
        Event event = createTestEvent();
        when(emailStrategy.canHandle(event)).thenReturn(true);
        when(smsStrategy.canHandle(event)).thenReturn(true);

        // When
        notificationService.processNotification(event);

        // Then
        verify(emailStrategy).canHandle(event);
        verify(emailStrategy).notify(event);
        verify(smsStrategy).canHandle(event);
        verify(smsStrategy).notify(event);
    }

    @Test
    void shouldNotProcessEventWhenNoStrategyMatches() {
        // Given
        Event event = createTestEvent();
        when(emailStrategy.canHandle(event)).thenReturn(false);
        when(smsStrategy.canHandle(event)).thenReturn(false);

        // When
        notificationService.processNotification(event);

        // Then
        verify(emailStrategy).canHandle(event);
        verify(emailStrategy, never()).notify(event);
        verify(smsStrategy).canHandle(event);
        verify(smsStrategy, never()).notify(event);
    }

    @Test
    void shouldHandleEmptyStrategyList() {
        // Given
        NotificationServiceImpl serviceWithNoStrategies = new NotificationServiceImpl(List.of());
        Event event = createTestEvent();

        // When/Then - should not throw exception
        serviceWithNoStrategies.processNotification(event);
    }

    @Test
    void shouldProcessEventWithSingleStrategy() {
        // Given
        NotificationServiceImpl serviceWithSingleStrategy = new NotificationServiceImpl(List.of(emailStrategy));
        Event event = createTestEvent();
        when(emailStrategy.canHandle(event)).thenReturn(true);

        // When
        serviceWithSingleStrategy.processNotification(event);

        // Then
        verify(emailStrategy).canHandle(event);
        verify(emailStrategy).notify(event);
    }
}
