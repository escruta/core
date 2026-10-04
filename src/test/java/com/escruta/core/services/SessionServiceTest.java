package com.escruta.core.services;

import com.escruta.core.dtos.session.SessionResponseDTO;
import com.escruta.core.entities.AccessToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionService Tests")
class SessionServiceTest {
    @Mock
    private TokenService tokenService;

    @Mock
    private UserService userService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2AuthenticatedPrincipal principal;

    @InjectMocks
    private SessionService sessionService;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should list sessions and flag the current one")
    void getSessions_shouldFlagCurrentSession() {
        AccessToken current = createSession("current-session", Instant.now());
        AccessToken other = createSession("other-session", Instant.now().minusSeconds(60));
        AccessToken legacy = createSession(null, null);

        when(userService.getUserId()).thenReturn(USER_ID);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getAttribute("sessionId")).thenReturn("current-session");
        when(tokenService.listSessions(USER_ID)).thenReturn(List.of(other, current, legacy));

        List<SessionResponseDTO> sessions = sessionService.getSessions();

        assertThat(sessions).hasSize(3);
        assertThat(sessions.getFirst().sessionId()).isEqualTo("current-session");
        assertThat(sessions.getFirst().current()).isTrue();
        assertThat(sessions).filteredOn(SessionResponseDTO::current).hasSize(1);
    }

    @Test
    @DisplayName("Should throw when user is not authenticated")
    void getSessions_shouldThrowWhenNotAuthenticated() {
        when(userService.getUserId()).thenReturn(null);

        assertThatThrownBy(() -> sessionService.getSessions()).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("Should revoke a session owned by the current user")
    void revokeSession_shouldReturnTrueWhenOwned() {
        when(userService.getUserId()).thenReturn(USER_ID);
        when(tokenService.revokeSession(USER_ID, "session-id")).thenReturn(true);

        boolean result = sessionService.revokeSession("session-id");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should revoke every session except the current one")
    void revokeOtherSessions_shouldKeepCurrentSession() {
        AccessToken current = createSession("current-session", Instant.now());
        AccessToken other = createSession("other-session", Instant.now().minusSeconds(60));

        when(userService.getUserId()).thenReturn(USER_ID);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getAttribute("sessionId")).thenReturn("current-session");
        when(tokenService.listSessions(USER_ID)).thenReturn(List.of(current, other));
        when(tokenService.revokeSession(USER_ID, "other-session")).thenReturn(true);

        int revoked = sessionService.revokeOtherSessions();

        assertThat(revoked).isEqualTo(1);
        verify(tokenService, never()).revokeSession(USER_ID, "current-session");
    }

    private AccessToken createSession(String sessionId, Instant createdAt) {
        AccessToken token = new AccessToken();
        token.setToken("hashed-" + sessionId);
        token.setUserId(USER_ID);
        token.setSessionId(sessionId);
        token.setCreatedAt(createdAt);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setUserAgent("JUnit");
        token.setIpAddress("127.0.0.1");
        return token;
    }
}
