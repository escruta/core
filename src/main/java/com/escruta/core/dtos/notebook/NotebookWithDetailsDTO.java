package com.escruta.core.dtos.notebook;

import com.escruta.core.dtos.BasicUser;
import com.escruta.core.dtos.note.NoteResponseDTO;
import com.escruta.core.dtos.source.SourceResponseDTO;
import com.escruta.core.entities.Notebook;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public record NotebookWithDetailsDTO(
        UUID id,
        BasicUser user,
        String icon,
        String title,
        Timestamp createdAt,
        Timestamp updatedAt,
        List<NoteResponseDTO> notes,
        List<SourceResponseDTO> sources
) {
    public NotebookWithDetailsDTO(Notebook notebook, List<NoteResponseDTO> notes, List<SourceResponseDTO> sources) {
        this(
                notebook.getId(),
                new BasicUser(notebook.getUser()),
                notebook.getIcon(),
                notebook.getTitle(),
                notebook.getCreatedAt(),
                notebook.getUpdatedAt(),
                notes,
                sources
        );
    }
}
