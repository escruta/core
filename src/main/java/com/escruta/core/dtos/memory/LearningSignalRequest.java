package com.escruta.core.dtos.memory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record LearningSignalRequest(
        @NotNull
        SignalSource source,
        @NotNull
        SignalOutcome outcome,
        @NotBlank
        @Size(max = 400)
        String topic,
        @Nullable
        @Size(max = 500)
        String detail
) {
    public enum SignalSource {
        FLASHCARD,
        QUESTIONNAIRE
    }

    public enum SignalOutcome {
        MASTERED,
        STRUGGLING
    }
}
