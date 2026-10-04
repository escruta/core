package com.escruta.core.services;

import com.escruta.core.entities.AccessToken;
import com.escruta.core.repositories.AccessTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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

    @Value("${security.session.expiration-interval-seconds}")
    private int sessionExpirationIntervalSeconds;

    @Transactional
    public AccessToken createToken(java.util.UUID userId) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String rawToken = base64Encoder.encodeToString(randomBytes);
        String hashedToken = hashToken(rawToken);

        AccessToken accessToken = new AccessToken();
        accessToken.setToken(hashedToken);
        accessToken.setUserId(userId);
        accessToken.setSessionId(UUID.randomUUID().toString());
        accessToken.setCreatedAt(java.time.Instant.now());
        accessToken.setUserAgent(resolveUserAgent());
        accessToken.setIpAddress(resolveIpAddress());
        accessToken.setExpiresAt(java.time.Instant.now().plusSeconds(this.sessionExpirationIntervalSeconds));
        accessToken.setTimeToLive((long) this.sessionExpirationIntervalSeconds);

        accessTokenRepository.save(accessToken);
        accessToken.setToken(rawToken);
        return accessToken;
    }

    public Optional<AccessToken> validateToken(String rawToken) {
        String hashedToken = hashToken(rawToken);
        return accessTokenRepository
                .findById(hashedToken)
                .filter(t -> t.getExpiresAt().isAfter(java.time.Instant.now()));
    }

    public java.util.List<AccessToken> listSessions(UUID userId) {
        return accessTokenRepository.findAllByUserId(userId);
    }

    @Transactional
    public boolean revokeSession(UUID userId, String sessionId) {
        return accessTokenRepository
                .findBySessionId(sessionId)
                .filter(session -> userId.equals(session.getUserId()))
                .map(session -> {
                    accessTokenRepository.deleteById(session.getToken());
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void invalidateToken(String rawToken) {
        String hashedToken = hashToken(rawToken);
        accessTokenRepository.deleteById(hashedToken);
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
