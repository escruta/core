package com.escruta.core.dtos;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank
        String userInput,
        String conversationId
) {
}
