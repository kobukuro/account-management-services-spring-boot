package com.peter.notificationservice.service.notification;

import com.peter.notificationservice.domain.event.Event;

public interface NotificationStrategy {
    boolean canHandle(Event event);
    void notify(Event event);
}
