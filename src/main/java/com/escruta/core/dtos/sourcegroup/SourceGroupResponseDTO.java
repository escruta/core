package com.escruta.core.dtos.sourcegroup;

import com.escruta.core.entities.SourceGroup;

import java.sql.Timestamp;
import java.util.UUID;

public record SourceGroupResponseDTO(
        UUID id,
        UUID notebookId,
        String title,
        String color,
        Timestamp createdAt,
        Timestamp updatedAt
) {
    public SourceGroupResponseDTO(SourceGroup group) {
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
