package com.escruta.core.dtos;

import com.escruta.core.entities.AccessToken;

public record AccessTokenResponse(
        String token,
        long expiresIn
) {
    public AccessTokenResponse(AccessToken accessToken) {
        this(
                accessToken.getToken(),
                (accessToken
                        .getExpiresAt()
                        .toEpochMilli() - System.currentTimeMillis())
        );
    }
}
