package com.escruta.core.dtos;

import jakarta.validation.constraints.NotBlank;

public record DeviceStartRequest(
        @NotBlank
        String deviceCode
) {
}
