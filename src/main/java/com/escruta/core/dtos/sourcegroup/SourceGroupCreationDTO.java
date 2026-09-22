package com.escruta.core.dtos.sourcegroup;

import jakarta.validation.constraints.NotBlank;

public record SourceGroupCreationDTO(
        @NotBlank
        String title,
        String color
) {
}
