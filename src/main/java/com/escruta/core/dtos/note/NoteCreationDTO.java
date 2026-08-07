package com.escruta.core.dtos.note;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record NoteCreationDTO(
        @NotNull
        UUID notebookId,
        @NotBlank
        String title,
        String content
) {
}
