package com.escruta.core.dtos.conversation;

import java.sql.Timestamp;

public record ConversationResponse(
        String id,
        String title,
        Timestamp createdAt,
        Timestamp updatedAt
) {
}
