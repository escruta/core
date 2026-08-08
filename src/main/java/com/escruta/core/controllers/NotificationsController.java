package com.escruta.core.controllers;

import com.escruta.core.services.SseNotificationService;
import com.escruta.core.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class NotificationsController {
    private final SseNotificationService sseNotificationService;
    private final UserService userService;

    @GetMapping(value = "/api/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications() {
        var user = userService.getCurrentUser();
        if (user == null) {
            throw new SecurityException("User not authenticated");
        }

        SseEmitter emitter = sseNotificationService.register(user.getId());

        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
        heartbeat.scheduleAtFixedRate(
                () -> {
                    try {
                        emitter.send(SseEmitter.event().comment("ping"));
                    } catch (Exception e) {
                        heartbeat.shutdown();
                    }
                }, 25, 25, TimeUnit.SECONDS
        );

        emitter.onCompletion(heartbeat::shutdown);
        emitter.onTimeout(heartbeat::shutdown);
        emitter.onError(_ -> heartbeat.shutdown());

        return emitter;
    }
}
