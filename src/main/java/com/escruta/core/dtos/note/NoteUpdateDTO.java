package com.escruta.core.dtos.note;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.UUID;

public record NoteUpdateDTO(
        @UUID
        @NotNull
        String id,
        String title,
        String content,
        java.util.UUID groupId,
        Boolean removeGroup
) {
    public NoteUpdateDTO(String id, String title, String content) {
        this(id, title, content, null, null);
    }
}
