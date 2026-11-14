package com.escruta.core.dtos;

import com.escruta.core.entities.enums.ChatMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ChatRequest(
        @NotBlank
        String userInput,
        String conversationId,
        List<UUID> selectedSourceIds,
        ChatMode mode,
        @Size(max = 32)
        String responseDetail,
        @Size(max = 500)
        String customInstructions
) {
    public ChatRequest(
            String userInput,
            String conversationId,
            List<UUID> selectedSourceIds,
            ChatMode mode
    ) {
        this(userInput, conversationId, selectedSourceIds, mode, null, null);
    }
}
