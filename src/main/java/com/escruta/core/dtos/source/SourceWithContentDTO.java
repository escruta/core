package com.escruta.core.dtos.source;

import com.escruta.core.entities.Source;
import com.escruta.core.entities.enums.SourceStatus;
import com.escruta.core.entities.enums.SourceType;

import java.sql.Timestamp;
import java.util.UUID;

public record SourceWithContentDTO(
        UUID id,
        UUID notebookId,
        String icon,
        String title,
        String content,
        boolean isConvertedByAi,
        String summary,
        String link,
        SourceStatus status,
        SourceType type,
        Timestamp createdAt,
        Timestamp updatedAt
) {
    public SourceWithContentDTO(Source source) {
        this(
                source.getId(),
                source.getNotebook().getId(),
                source.getIcon(),
                source.getTitle(),
                source.getContent(),
                source.isConvertedByAi(),
                source.getSummary(),
                source.getLink(),
                source.getStatus(),
                source.getType(),
                source.getCreatedAt(),
                source.getUpdatedAt()
        );
    }
}
