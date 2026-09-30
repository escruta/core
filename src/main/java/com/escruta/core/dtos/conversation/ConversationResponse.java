package com.escruta.core.dtos.conversation;

import com.escruta.core.entities.enums.ChatMode;

import java.sql.Timestamp;

public record ConversationResponse(
        String id,
        String title,
        ChatMode mode,
        Timestamp createdAt,
        Timestamp updatedAt
) {
}
