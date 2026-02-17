package com.escruta.core.dtos;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record ChatRequest(
        @NotBlank
        String userInput,
        String conversationId,
        List<UUID> selectedSourceIds
) {
}
