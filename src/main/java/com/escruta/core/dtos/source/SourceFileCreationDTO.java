package com.escruta.core.dtos.source;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record SourceFileCreationDTO(
        String icon,
        @NotBlank
        String title,
        UUID groupId
) {
}
