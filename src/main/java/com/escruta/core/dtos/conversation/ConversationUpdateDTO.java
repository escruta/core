package com.escruta.core.dtos.conversation;

import jakarta.validation.constraints.NotBlank;

public record ConversationUpdateDTO(
        @NotBlank
        String title
) {
}
