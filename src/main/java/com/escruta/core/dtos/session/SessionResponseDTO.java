package com.escruta.core.dtos.session;

import java.time.Instant;

public record SessionResponseDTO(
        String sessionId,
        String userAgent,
        String ipAddress,
        Instant createdAt,
        Instant expiresAt,
        boolean current
) {
}
