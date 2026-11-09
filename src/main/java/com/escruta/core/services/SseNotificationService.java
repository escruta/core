package com.escruta.core.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
@RequiredArgsConstructor
@Slf4j
public class SseNotificationService {
    public static final String REDIS_CHANNEL = "escruta:sse";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<UUID, Set<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public record SseMessage(
            UUID userId,
            String eventName,
            Object data
    ) {
    }

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

    @Scheduled(fixedRateString = "${services.sse.heartbeat-ms:25000}")
    public void heartbeatAll() {
        emittersByUser.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    unregister(userId, emitter);
                }
            }
        });
    }

    public void publish(UUID userId, String eventName, Object data) {
        try {
            // Fan out to every application instance. The local instance also
            // receives the message through its Redis subscription.
            redisTemplate.convertAndSend(
                    REDIS_CHANNEL,
                    objectMapper.writeValueAsString(new SseMessage(userId, eventName, data))
            );
        } catch (Exception e) {
            log.warn("Redis SSE fan-out failed, publishing locally: {}", e.getMessage());
            publishLocal(userId, eventName, data);
        }
    }

    public void publishLocal(UUID userId, String eventName, Object data) {
        Set<SseEmitter> userEmitters = emittersByUser.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
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
