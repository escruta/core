package com.escruta.core.dtos.conversation;

import java.util.List;

public record ConversationMessagesResponse(
        String conversationId,
        List<MessageResponse> messages
) {
    public record MessageResponse(
            String content,
            String type
    ) {
    }
}
