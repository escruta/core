package com.escruta.core.dtos.note;

import com.escruta.core.entities.Note;

import java.sql.Timestamp;
import java.util.UUID;

public record NoteWithContentDTO(
        UUID id,
        UUID notebookId,
        UUID folderId,
        UUID sourceId,
        String title,
        String content,
        Timestamp createdAt,
        Timestamp updatedAt
) {
    public NoteWithContentDTO(Note note) {
        this(
                note.getId(),
                note.getNotebook() != null ?
                        note.getNotebook().getId() :
                        null,
                note.getFolder() != null ?
                        note.getFolder().getId() :
                        null,
                note.getSource() != null ?
                        note.getSource().getId() :
                        null,
                note.getTitle(),
                note.getContent(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
