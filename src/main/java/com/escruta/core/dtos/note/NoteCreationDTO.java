package com.escruta.core.dtos.note;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record NoteCreationDTO(
        UUID notebookId,
        String icon,
        @NotBlank
        String title,
        String content
) {
}
