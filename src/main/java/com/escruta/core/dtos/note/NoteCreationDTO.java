package com.escruta.core.dtos.note;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record NoteCreationDTO(
        UUID notebookId,
        UUID folderId,
        @NotBlank
        String title,
        String content
) {
}
