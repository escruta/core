package com.escruta.core.services;

import com.escruta.core.exceptions.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {
    private final StringRedisTemplate redisTemplate;

    @Value("${services.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${services.rate-limit.auth-requests-per-window:10}")
    private int authRequestsPerWindow;

    @Value("${services.rate-limit.chat-requests-per-window:30}")
    private int chatRequestsPerWindow;

    @Value("${services.rate-limit.window-seconds:60}")
    private long windowSeconds;

    public void checkAuthRequest(String email) {
        check("auth", email + ":" + clientIp(), authRequestsPerWindow);
    }

    public void checkChatRequest(UUID userId) {
        String key = userId != null ?
                userId.toString() :
                clientIp();
        check("chat", key, chatRequestsPerWindow);
    }

    private void check(String bucket, String key, int limit) {
        if (!enabled) {
            return;
        }

        try {
            String redisKey = "rate:" + bucket + ":" + key;
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
            }
            if (count != null && count > limit) {
                throw new RateLimitExceededException("Too many requests. Please wait a moment and try again.");
            }
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            // Never let a Redis outage take down authentication or chat.
            log.warn("Rate limit check failed for bucket {}: {}", bucket, e.getMessage());
        }
    }

    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return "unknown";
    }
}
