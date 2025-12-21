package com.escruta.core.dtos.notebook;

import com.escruta.core.dtos.BasicUser;
import com.escruta.core.entities.Notebook;

import java.sql.Timestamp;
import java.util.UUID;

public record NotebookResponseDTO(
        UUID id,
        BasicUser user,
        String icon,
        String title,
        Timestamp createdAt,
        Timestamp updatedAt
) {
    public NotebookResponseDTO(Notebook notebook) {
        this(
                notebook.getId(),
                new BasicUser(notebook.getUser()),
                notebook.getIcon(),
                notebook.getTitle(),
                notebook.getCreatedAt(),
                notebook.getUpdatedAt()
        );
    }
}
