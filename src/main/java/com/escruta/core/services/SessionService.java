package com.escruta.core.services;

import com.escruta.core.dtos.session.SessionResponseDTO;
import com.escruta.core.entities.AccessToken;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final TokenService tokenService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<SessionResponseDTO> getSessions() {
        UUID userId = requireUserId();
        String currentSessionId = getCurrentSessionId();

        return tokenService
                .listSessions(userId)
                .stream()
                .sorted(Comparator.comparing(AccessToken::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .map(session -> new SessionResponseDTO(
                        session.getSessionId(),
                        session.getUserAgent(),
                        session.getIpAddress(),
                        session.getCreatedAt(),
                        session.getExpiresAt(),
                        Objects.equals(session.getSessionId(), currentSessionId)
                ))
                .toList();
    }

    @Transactional
    public boolean revokeSession(String sessionId) {
        UUID userId = requireUserId();
        return tokenService.revokeSession(userId, sessionId);
    }

    @Transactional
    public int revokeOtherSessions() {
        UUID userId = requireUserId();
        String currentSessionId = getCurrentSessionId();
        if (currentSessionId == null) {
            return 0;
        }

        int revoked = 0;
        for (AccessToken session : tokenService.listSessions(userId)) {
            if (currentSessionId.equals(session.getSessionId())) {
                continue;
            }
            if (tokenService.revokeSession(userId, session.getSessionId())) {
                revoked++;
            }
        }
        return revoked;
    }

    private UUID requireUserId() {
        UUID userId = userService.getUserId();
        if (userId == null) {
            throw new BadCredentialsException("User not authenticated");
        }
        return userId;
    }

    private String getCurrentSessionId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
            Object sessionId = principal.getAttribute("sessionId");
            return sessionId != null ?
                    sessionId.toString() :
                    null;
        }
        return null;
    }
}
