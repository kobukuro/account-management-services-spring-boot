package com.peter.notificationservice.service;

import com.peter.notificationservice.domain.event.Event;

public interface NotificationService {
    void processNotification(Event event);
}
