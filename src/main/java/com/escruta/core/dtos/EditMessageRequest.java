package com.escruta.core.dtos;

import com.escruta.core.entities.enums.ChatMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record EditMessageRequest(
        @NotBlank
        String userInput,
        List<UUID> selectedSourceIds,
        ChatMode mode,
        @Size(max = 32)
        String responseDetail,
        @Size(max = 500)
        String customInstructions
) {
}
