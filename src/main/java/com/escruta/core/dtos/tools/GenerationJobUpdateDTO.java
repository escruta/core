package com.escruta.core.dtos.tools;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerationJobUpdateDTO(
        @NotBlank
        @Size(max = 255)
        String title
) {
}
