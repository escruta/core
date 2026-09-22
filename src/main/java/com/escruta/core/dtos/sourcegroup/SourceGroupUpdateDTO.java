package com.escruta.core.dtos.sourcegroup;

import jakarta.validation.constraints.NotBlank;

public record SourceGroupUpdateDTO(
        @NotBlank
        String title,
        String color
) {
}
