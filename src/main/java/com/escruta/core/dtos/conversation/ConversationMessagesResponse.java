package com.escruta.core.dtos.conversation;

import com.escruta.core.dtos.ChatReplyMessage;

import java.util.List;

public record ConversationMessagesResponse(
        String conversationId,
        List<MessageResponse> messages
) {
    public record MessageResponse(
            String content,
            String type,
            List<ChatReplyMessage.CitedSource> citedSources,
            Integer selectedSourcesCount
    ) {
    }
}
