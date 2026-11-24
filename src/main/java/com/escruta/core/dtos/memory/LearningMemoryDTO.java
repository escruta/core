package com.escruta.core.dtos.memory;

import com.escruta.core.entities.enums.ProficiencyLevel;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LearningMemoryDTO(
        UUID notebookId,
        boolean enabled,
        ProficiencyLevel proficiencyLevel,
        @Nullable
        String learningGoal,
        List<String> masteredConcepts,
        List<String> strugglingConcepts,
        @Nullable
        String studyNotes,
        PracticeStats stats,
        @Nullable
        Instant updatedAt
) {
    public record PracticeStats(
            int flashcardsReviewed,
            int flashcardsKnown,
            int quizQuestionsAnswered,
            int quizQuestionsCorrect,
            int learningTurnsCount
    ) {
    }

    public static LearningMemoryDTO empty(UUID notebookId) {
        return new LearningMemoryDTO(
                notebookId,
                true,
                ProficiencyLevel.AUTO,
                null,
                List.of(),
                List.of(),
                null,
                new PracticeStats(0, 0, 0, 0, 0),
                null
        );
    }
}
