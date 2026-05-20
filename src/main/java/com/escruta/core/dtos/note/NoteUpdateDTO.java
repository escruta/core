package com.escruta.core.dtos.note;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.UUID;

public record NoteUpdateDTO(
        @UUID
        @NotNull
        String id,
        java.util.UUID folderId,
        Boolean removeFolder,
        String title,
        String content
) {
}
