package com.escruta.core.dtos.note;

import com.escruta.core.entities.Note;

import java.sql.Timestamp;
import java.util.UUID;

public record NoteResponseDTO(
        UUID id,
        UUID notebookId,
        String title,
        Timestamp createdAt,
        Timestamp updatedAt
) {
    public NoteResponseDTO(Note note) {
        this(
                note.getId(),
                note.getNotebook().getId(),
                note.getTitle(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
