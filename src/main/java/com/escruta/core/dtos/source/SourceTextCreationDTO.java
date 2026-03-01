package com.escruta.core.dtos.source;

import jakarta.validation.constraints.NotBlank;

public record SourceTextCreationDTO(
        String icon,
        @NotBlank
        String title,
        @NotBlank
        String content
) {
}
