package com.peter.notificationservice.service.notification;

import com.peter.notificationservice.domain.event.UserRegistrationEvent;

public interface NotificationStrategy {
    boolean canHandle(UserRegistrationEvent event);
    void notify(UserRegistrationEvent event);
}
