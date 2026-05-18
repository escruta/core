package com.escruta.core.dtos;

import java.util.List;
import java.util.UUID;

public record ChatReplyMessage(
        String content,
        String conversationId,
        String conversationTitle,
        List<CitedSource> citedSources
) {

    public record CitedSource(
            UUID id,
            UUID documentId,
            String title,
            String text
    ) {
    }
}
