package com.escruta.core.dtos.conversation;

import java.util.List;

public record ConversationsPageResponse(
        List<ConversationResponse> conversations,
        long total,
        boolean hasMore
) {
}
