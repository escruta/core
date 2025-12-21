package com.escruta.core.dtos.note;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.UUID;

public record NoteUpdateDTO(
        @UUID
        @NotNull
        String id,
        String icon,
        String title,
        String content
) {
}
