package com.escruta.core.services;

import com.escruta.core.entities.AccessToken;
import com.escruta.core.repositories.AccessTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenService Tests")
class TokenServiceTest {
    @Mock
    private AccessTokenRepository accessTokenRepository;

    @InjectMocks
    private TokenService tokenService;

    private static final int SESSION_EXPIRATION_SECONDS = 3600;
    private static final String TEST_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tokenService, "sessionExpirationIntervalSeconds", SESSION_EXPIRATION_SECONDS);
    }

    @Test
    @DisplayName("Should create token with correct properties")
    void createToken_shouldCreateTokenWithCorrectProperties() {
        when(accessTokenRepository.save(any(AccessToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccessToken result = tokenService.createToken(TEST_EMAIL);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(result.getToken()).isNotNull().isNotEmpty();
        assertThat(result.getExpiresAt()).isAfter(Instant.now());
        assertThat(result.getExpiresAt()).isBefore(Instant.now().plusSeconds(SESSION_EXPIRATION_SECONDS + 1));
        assertThat(result.getExpiresAt()).isAfter(Instant.now().plusSeconds(SESSION_EXPIRATION_SECONDS - 1));

        ArgumentCaptor<AccessToken> tokenCaptor = ArgumentCaptor.forClass(AccessToken.class);
        verify(accessTokenRepository, times(1)).save(tokenCaptor.capture());

        AccessToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(savedToken.getExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("Should create different tokens on each call")
    void createToken_shouldCreateDifferentTokensOnEachCall() {
        when(accessTokenRepository.save(any(AccessToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccessToken token1 = tokenService.createToken(TEST_EMAIL);
        AccessToken token2 = tokenService.createToken(TEST_EMAIL);

        assertThat(token1.getToken()).isNotEqualTo(token2.getToken());
    }

    @Test
    @DisplayName("Should validate existing and non-expired token")
    void validateToken_shouldReturnTokenWhenExistsAndNotExpired() {
        AccessToken accessToken = new AccessToken();
        accessToken.setEmail(TEST_EMAIL);
        accessToken.setToken("hashedToken");
        accessToken.setExpiresAt(Instant.now().plusSeconds(100));

        when(accessTokenRepository.findByToken(any())).thenReturn(Optional.of(accessToken));

        AccessToken createdToken = tokenService.createToken(TEST_EMAIL);
        Optional<AccessToken> result = tokenService.validateToken(createdToken.getToken());

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo(TEST_EMAIL);
    }

    @Test
    @DisplayName("Should return empty when token is expired")
    void validateToken_shouldReturnEmptyWhenTokenIsExpired() {
        AccessToken expiredToken = new AccessToken();
        expiredToken.setEmail(TEST_EMAIL);
        expiredToken.setToken("hashedToken");
        expiredToken.setExpiresAt(Instant.now().minusSeconds(100));

        when(accessTokenRepository.findByToken(any())).thenReturn(Optional.of(expiredToken));

        AccessToken createdToken = tokenService.createToken(TEST_EMAIL);
        Optional<AccessToken> result = tokenService.validateToken(createdToken.getToken());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when token does not exist")
    void validateToken_shouldReturnEmptyWhenTokenDoesNotExist() {
        when(accessTokenRepository.findByToken(any())).thenReturn(Optional.empty());

        Optional<AccessToken> result = tokenService.validateToken("nonExistentToken123");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should invalidate token by deleting it")
    void invalidateToken_shouldDeleteToken() {
        tokenService.invalidateToken("someToken");

        verify(accessTokenRepository, times(1)).deleteById(any());
    }

    @Test
    @DisplayName("Should hash token consistently")
    void hashToken_shouldBeConsistent() {
        when(accessTokenRepository.save(any(AccessToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccessToken token1 = tokenService.createToken(TEST_EMAIL);
        AccessToken token2 = tokenService.createToken(TEST_EMAIL);

        assertThat(token1.getToken()).isNotEqualTo(token2.getToken());
    }
}
