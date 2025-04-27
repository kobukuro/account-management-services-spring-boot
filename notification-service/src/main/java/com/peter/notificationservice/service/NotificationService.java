package com.peter.notificationservice.service;

import com.peter.notificationservice.domain.event.UserRegistrationEvent;
import com.peter.notificationservice.service.notification.NotificationStrategy;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {
    private final List<NotificationStrategy> notificationStrategies;

    public NotificationService(List<NotificationStrategy> notificationStrategies) {
        this.notificationStrategies = notificationStrategies;
    }

    public void processNotification(UserRegistrationEvent event) {
        // Iterate through the list of notification strategies and find the ones that can handle the event
        notificationStrategies.stream()
                .filter(strategy -> strategy.canHandle(event))
                .forEach(strategy -> strategy.notify(event));
    }
}
