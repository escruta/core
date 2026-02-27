package com.escruta.core.dtos.source;

import com.escruta.core.entities.Source;
import com.escruta.core.entities.enums.SourceStatus;

import java.sql.Timestamp;
import java.util.UUID;

public record SourceResponseDTO(
        UUID id,
        UUID notebookId,
        String icon,
        String title,
        boolean isConvertedByAi,
        String link,
        SourceStatus status,
        Timestamp createdAt,
        Timestamp updatedAt
) {
    public SourceResponseDTO(Source source) {
        this(
                source.getId(),
                source.getNotebook().getId(),
                source.getIcon(),
                source.getTitle(),
                source.isConvertedByAi(),
                source.getLink(),
                source.getStatus(),
                source.getCreatedAt(),
                source.getUpdatedAt()
        );
    }
}
