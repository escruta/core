package com.escruta.core.dtos;

import java.util.List;

public record ChatReplyMessage(
        String content,
        String conversationId,
        String conversationTitle,
        List<CitedSource> citedSources,
        List<AgentStep> thinkingSteps,
        Long thinkingDurationMs
) {
    public record CitedSource(
            String id,
            String documentId,
            String title,
            String text
    ) {
    }
}
