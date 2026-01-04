package com.escruta.core.services;

import com.escruta.core.entities.AccessToken;
import com.escruta.core.repositories.AccessTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TokenService {
    private final AccessTokenRepository accessTokenRepository;
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();

    @Value("${security.session.expiration-interval-seconds}")
    private int sessionExpirationIntervalSeconds;

    @Transactional
    public AccessToken createToken(String email) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String rawToken = base64Encoder.encodeToString(randomBytes);
        String hashedToken = hashToken(rawToken);

        AccessToken accessToken = new AccessToken();
        accessToken.setToken(hashedToken);
        accessToken.setEmail(email);
        accessToken.setExpiresAt(Instant.now().plusSeconds(this.sessionExpirationIntervalSeconds));

        accessTokenRepository.save(accessToken);
        accessToken.setToken(rawToken);
        return accessToken;
    }

    public Optional<AccessToken> validateToken(String rawToken) {
        String hashedToken = hashToken(rawToken);
        return accessTokenRepository.findByToken(hashedToken).filter(t -> t.getExpiresAt().isAfter(Instant.now()));
    }

    @Transactional
    public void invalidateToken(String rawToken) {
        String hashedToken = hashToken(rawToken);
        accessTokenRepository.deleteById(hashedToken);
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
