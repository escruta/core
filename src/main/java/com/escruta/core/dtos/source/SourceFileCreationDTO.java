package com.escruta.core.dtos.source;

import jakarta.validation.constraints.NotBlank;

public record SourceFileCreationDTO(
        String icon,
        @NotBlank
        String title
) {
}
