package com.escruta.core.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
@RequiredArgsConstructor
@Slf4j
public class SseNotificationService {
    private final ConcurrentHashMap<UUID, Set<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter register(UUID userId) {
        SseEmitter emitter = new SseEmitter(0L);
        Set<SseEmitter> userEmitters = emittersByUser.computeIfAbsent(userId, _ -> new CopyOnWriteArraySet<>());
        userEmitters.add(emitter);

        emitter.onCompletion(() -> unregister(userId, emitter));
        emitter.onTimeout(() -> unregister(userId, emitter));
        emitter.onError(_ -> unregister(userId, emitter));

        log.debug("Registered SSE emitter for user {}", userId);
        return emitter;
    }

    public void publish(UUID userId, String eventName, Object data) {
        Set<SseEmitter> userEmitters = emittersByUser.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                unregister(userId, emitter);
            }
        }
    }

    private void unregister(UUID userId, SseEmitter emitter) {
        Set<SseEmitter> userEmitters = emittersByUser.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emittersByUser.remove(userId);
            }
        }
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
        }
    }
}
