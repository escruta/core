package com.escruta.core.dtos;

import com.escruta.core.entities.enums.ChatMode;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record ChatRequest(
        @NotBlank
        String userInput,
        String conversationId,
        List<UUID> selectedSourceIds,
        ChatMode mode
) {
}
