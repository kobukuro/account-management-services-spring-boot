package com.peter.notificationservice.service;

import com.peter.notificationservice.domain.event.UserRegistrationEvent;

public interface NotificationService {
    void processNotification(UserRegistrationEvent event);
}
