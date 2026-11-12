package com.escruta.core.dtos.notegroup;

import com.escruta.core.entities.NoteGroup;

import java.sql.Timestamp;
import java.util.UUID;

public record NoteGroupResponseDTO(
        UUID id,
        UUID notebookId,
        String title,
        String color,
        Timestamp createdAt,
        Timestamp updatedAt
) {
    public NoteGroupResponseDTO(NoteGroup group) {
        this(
                group.getId(),
                group.getNotebook() != null ?
                        group.getNotebook().getId() :
                        null,
                group.getTitle(),
                group.getColor(),
                group.getCreatedAt(),
                group.getUpdatedAt()
        );
    }
}
