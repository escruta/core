package com.escruta.core.services;

import com.escruta.core.entities.AccessToken;
import com.escruta.core.repositories.AccessTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TokenService {
    private final AccessTokenRepository accessTokenRepository;
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder();

    @Value("${security.session.expiration-interval-seconds}")
    private int sessionExpirationIntervalSeconds;

    @Transactional
    public AccessToken createToken(String email) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        AccessToken accessToken = new AccessToken();
        accessToken.setToken(base64Encoder.encodeToString(randomBytes));
        accessToken.setEmail(email);
        accessToken.setExpiresAt(Instant.now().plusSeconds(this.sessionExpirationIntervalSeconds));

        accessTokenRepository.save(accessToken);
        return accessToken;
    }

    public Optional<AccessToken> validateToken(String token) {
        return accessTokenRepository.findByToken(token).filter(t -> t.getExpiresAt().isAfter(Instant.now()));
    }

    @Transactional
    public void invalidateToken(String token) {
        accessTokenRepository.deleteById(token);
    }
}
