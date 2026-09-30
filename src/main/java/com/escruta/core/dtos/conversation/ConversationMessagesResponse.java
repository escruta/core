package com.escruta.core.dtos.conversation;

import com.escruta.core.dtos.ChatReplyMessage;
import com.escruta.core.entities.enums.ChatMode;

import java.util.List;

public record ConversationMessagesResponse(
        String conversationId,
        ChatMode mode,
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
