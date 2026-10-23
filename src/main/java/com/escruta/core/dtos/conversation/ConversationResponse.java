package com.escruta.core.dtos.conversation;

import com.escruta.core.entities.enums.ChatMode;
import com.escruta.core.entities.enums.ConversationStatus;

import java.sql.Timestamp;

public record ConversationResponse(
        String id,
        String title,
        ChatMode mode,
        ConversationStatus status,
        boolean hasUnseenReply,
        Timestamp createdAt,
        Timestamp updatedAt
) {
}
