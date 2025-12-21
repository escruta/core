package com.escruta.core.dtos.notebook;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.UUID;

public record NotebookUpdateDTO(
        @UUID
        @NotNull
        String id,
        String icon,
        String title
) {
}
