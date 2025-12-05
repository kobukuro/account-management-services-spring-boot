package com.peter.notificationservice.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peter.notificationservice.domain.event.Email;
import com.peter.notificationservice.domain.event.Event;
import com.peter.notificationservice.domain.event.UserDetails;
import com.peter.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationEventConsumer.
 * Tests Kafka event consumption and DLT (Dead Letter Topic) handling.
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private NotificationService notificationService;

    private NotificationEventConsumer consumer;

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
        consumer = new NotificationEventConsumer(objectMapper, notificationService);
    }

    @Test
    void shouldHandleNotificationSuccessfully() throws JsonProcessingException {
        // Given
        Event event = createTestEvent();
        String topic = "user-registration-topic";
        long offset = 123L;
        String jsonString = "{\"userDetails\":{\"firstName\":\"John\",\"lastName\":\"Doe\",\"email\":\"john.doe@example.com\"}}";

        when(objectMapper.writeValueAsString(event)).thenReturn(jsonString);

        // When
        consumer.handleNotification(event, topic, offset);

        // Then
        verify(objectMapper).writeValueAsString(event);
        verify(notificationService).processNotification(event);
    }

    @Test
    void shouldHandleNotificationWhenJsonProcessingFails() throws JsonProcessingException {
        // Given
        Event event = createTestEvent();
        String topic = "user-registration-topic";
        long offset = 123L;
        JsonProcessingException exception = mock(JsonProcessingException.class);

        when(objectMapper.writeValueAsString(event)).thenThrow(exception);

        // When/Then
        assertThrows(JsonProcessingException.class, () ->
                consumer.handleNotification(event, topic, offset)
        );

        verify(objectMapper).writeValueAsString(event);
        verify(notificationService, never()).processNotification(event);
    }

    @Test
    void shouldProcessNotificationEvenWhenServiceThrowsException() throws JsonProcessingException {
        // Given
        Event event = createTestEvent();
        String topic = "user-registration-topic";
        long offset = 123L;
        String jsonString = "{\"userDetails\":{\"firstName\":\"John\",\"lastName\":\"Doe\",\"email\":\"john.doe@example.com\"}}";

        when(objectMapper.writeValueAsString(event)).thenReturn(jsonString);
        doThrow(new RuntimeException("Service error")).when(notificationService).processNotification(event);

        // When/Then
        assertThrows(RuntimeException.class, () ->
                consumer.handleNotification(event, topic, offset)
        );

        verify(objectMapper).writeValueAsString(event);
        verify(notificationService).processNotification(event);
    }

    @Test
    void shouldHandleDLTSuccessfully() throws JsonProcessingException {
        // Given
        Event event = createTestEvent();
        String topic = "user-registration-topic.DLT";
        long offset = 456L;
        String jsonString = "{\"userDetails\":{\"firstName\":\"John\",\"lastName\":\"Doe\",\"email\":\"john.doe@example.com\"}}";

        when(objectMapper.writeValueAsString(event)).thenReturn(jsonString);

        // When
        consumer.listenDLT(event, topic, offset);

        // Then
        verify(objectMapper).writeValueAsString(event);
        verifyNoInteractions(notificationService);
    }

    @Test
    void shouldHandleDLTWhenJsonProcessingFails() throws JsonProcessingException {
        // Given
        Event event = createTestEvent();
        String topic = "user-registration-topic.DLT";
        long offset = 456L;
        JsonProcessingException exception = mock(JsonProcessingException.class);

        when(objectMapper.writeValueAsString(event)).thenThrow(exception);

        // When/Then
        assertThrows(JsonProcessingException.class, () ->
                consumer.listenDLT(event, topic, offset)
        );

        verify(objectMapper).writeValueAsString(event);
        verifyNoInteractions(notificationService);
    }

    @Test
    void shouldHandleNotificationWithDifferentOffsets() throws JsonProcessingException {
        // Given
        Event event = createTestEvent();
        String topic = "user-registration-topic";
        long offset1 = 0L;
        long offset2 = Long.MAX_VALUE;
        String jsonString = "{\"userDetails\":{\"firstName\":\"John\",\"lastName\":\"Doe\",\"email\":\"john.doe@example.com\"}}";

        when(objectMapper.writeValueAsString(event)).thenReturn(jsonString);

        // When
        consumer.handleNotification(event, topic, offset1);
        consumer.handleNotification(event, topic, offset2);

        // Then
        verify(objectMapper, times(2)).writeValueAsString(event);
        verify(notificationService, times(2)).processNotification(event);
    }

    @Test
    void shouldHandleNotificationWithDifferentTopics() throws JsonProcessingException {
        // Given
        Event event = createTestEvent();
        String topic1 = "user-registration-topic";
        String topic2 = "user-verification-topic";
        long offset = 100L;
        String jsonString = "{\"userDetails\":{\"firstName\":\"John\",\"lastName\":\"Doe\",\"email\":\"john.doe@example.com\"}}";

        when(objectMapper.writeValueAsString(event)).thenReturn(jsonString);

        // When
        consumer.handleNotification(event, topic1, offset);
        consumer.handleNotification(event, topic2, offset);

        // Then
        verify(objectMapper, times(2)).writeValueAsString(event);
        verify(notificationService, times(2)).processNotification(event);
    }

    @Test
    void shouldHandleDifferentEventTypes() throws JsonProcessingException {
        // Given
        Event event1 = new Event(
                new UserDetails("John", "Doe", "john@example.com"),
                new Email("Subject 1", "template1", Map.of("k1", "v1"))
        );
        Event event2 = new Event(
                new UserDetails("Jane", "Smith", "jane@example.com"),
                new Email("Subject 2", "template2", Map.of("k2", "v2"))
        );
        String topic = "user-registration-topic";
        long offset = 200L;

        when(objectMapper.writeValueAsString(event1)).thenReturn("event1-json");
        when(objectMapper.writeValueAsString(event2)).thenReturn("event2-json");

        // When
        consumer.handleNotification(event1, topic, offset);
        consumer.handleNotification(event2, topic, offset + 1);

        // Then
        verify(objectMapper).writeValueAsString(event1);
        verify(objectMapper).writeValueAsString(event2);
        verify(notificationService).processNotification(event1);
        verify(notificationService).processNotification(event2);
    }
}
