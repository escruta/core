package com.escruta.core.services;

import com.escruta.core.entities.AccessToken;
import com.escruta.core.repositories.AccessTokenRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenService {
    private final AccessTokenRepository accessTokenRepository;
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();
    private static final int MAX_USER_AGENT_LENGTH = 500;

    /**
     * Short-lived local cache for opaque-token introspection. It removes a Redis
     * round-trip from every authenticated request while keeping revocation delay
     * bounded to a few seconds.
     */
    private final Cache<String, AccessToken> validationCache = Caffeine
            .newBuilder()
            .maximumSize(20_000)
            .expireAfterWrite(Duration.ofSeconds(3))
            .build();

    @Value("${security.session.expiration-interval-seconds}")
    private int sessionExpirationIntervalSeconds;

    public AccessToken createToken(UUID userId) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String rawToken = base64Encoder.encodeToString(randomBytes);
        String hashedToken = hashToken(rawToken);

        AccessToken accessToken = new AccessToken();
        accessToken.setToken(hashedToken);
        accessToken.setUserId(userId);
        accessToken.setSessionId(UUID.randomUUID().toString());
        accessToken.setCreatedAt(Instant.now());
        accessToken.setUserAgent(resolveUserAgent());
        accessToken.setIpAddress(resolveIpAddress());
        accessToken.setExpiresAt(Instant.now().plusSeconds(this.sessionExpirationIntervalSeconds));
        accessToken.setTimeToLive((long) this.sessionExpirationIntervalSeconds);

        accessTokenRepository.save(accessToken);
        accessToken.setToken(rawToken);
        return accessToken;
    }

    public Optional<AccessToken> validateToken(String rawToken) {
        String hashedToken = hashToken(rawToken);
        AccessToken cached = validationCache.getIfPresent(hashedToken);
        if (cached != null) {
            return cached.getExpiresAt().isAfter(Instant.now()) ?
                    Optional.of(cached) :
                    Optional.empty();
        }

        return accessTokenRepository
                .findById(hashedToken)
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .map(token -> {
                    validationCache.put(hashedToken, token);
                    return token;
                });
    }

    public java.util.List<AccessToken> listSessions(UUID userId) {
        return accessTokenRepository.findAllByUserId(userId);
    }

    public boolean revokeSession(UUID userId, String sessionId) {
        return accessTokenRepository
                .findBySessionId(sessionId)
                .filter(session -> userId.equals(session.getUserId()))
                .map(session -> {
                    accessTokenRepository.deleteById(session.getToken());
                    validationCache.invalidate(session.getToken());
                    return true;
                })
                .orElse(false);
    }

    public void invalidateToken(String rawToken) {
        String hashedToken = hashToken(rawToken);
        accessTokenRepository.deleteById(hashedToken);
        validationCache.invalidate(hashedToken);
    }

    private String resolveUserAgent() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent.length() > MAX_USER_AGENT_LENGTH ?
                userAgent.substring(0, MAX_USER_AGENT_LENGTH) :
                userAgent;
    }

    private String resolveIpAddress() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return new String(Hex.encode(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
