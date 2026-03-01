package com.escruta.core.dtos.source;

import com.escruta.core.entities.Source;
import com.escruta.core.entities.enums.SourceStatus;
import com.escruta.core.entities.enums.SourceType;

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
        SourceType type,
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
                source.getType(),
                source.getCreatedAt(),
                source.getUpdatedAt()
        );
    }
}
