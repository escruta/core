package com.escruta.core.dtos.source;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record SourceTextCreationDTO(
        String icon,
        @NotBlank
        String title,
        @NotBlank
        String content,
        UUID groupId
) {
}
