package com.escruta.core.services;

import com.escruta.core.dtos.AccessTokenResponse;

public record DevicePollResult(
        DevicePollResult.Status status,
        AccessTokenResponse token
) {
    public enum Status {
        PENDING,
        EXPIRED,
        AUTHORIZED,
    }
}
