package com.escruta.core.dtos;

import java.util.List;

public record ChatReplyMessage(
        String content,
        String conversationId,
        String conversationTitle,
        List<CitedSource> citedSources
) {

    public record CitedSource(
            String id,
            String documentId,
            String title,
            String text
    ) {
    }
}
