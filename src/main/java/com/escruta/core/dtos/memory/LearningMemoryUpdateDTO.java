package com.escruta.core.dtos.memory;

import com.escruta.core.entities.enums.ProficiencyLevel;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record LearningMemoryUpdateDTO(
        @Nullable
        Boolean enabled,
        @Nullable
        ProficiencyLevel proficiencyLevel,
        @Nullable
        @Size(max = 500)
        String learningGoal,
        @Nullable
        @Size(max = 30)
        List<String> masteredConcepts,
        @Nullable
        @Size(max = 30)
        List<String> strugglingConcepts,
        @Nullable
        @Size(max = 1500)
        String studyNotes
) {
}
