package com.escruta.core.dtos.tools;

import com.escruta.core.entities.GenerationJob.JobType;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record GenerationOptions(
        Difficulty difficulty,
        DetailLevel detailLevel,
        Integer itemCount,
        Integer maxDepth,
        Integer branchCount,
        List<String> questionTypes,
        List<String> sections,
        @Size(max = 500)
        String focusTopic,
        List<UUID> selectedSourceIds,
        Integer sourceCount
) {
    public static final List<String> VALID_QUESTION_TYPES = List.of("multiple_choice", "true_false", "short_answer");

    public static final List<String> VALID_STUDY_GUIDE_SECTIONS = List.of(
            "keyConcepts",
            "importantDetails",
            "connections",
            "reviewQuestions"
    );

    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    public enum DetailLevel {
        CONCISE,
        STANDARD,
        DETAILED
    }

    public static GenerationOptions normalizeFor(
            JobType type,
            GenerationOptions raw,
            List<UUID> readyNotebookSourceIds
    ) {
        Difficulty difficulty = raw != null && raw.difficulty() != null ?
                raw.difficulty() :
                Difficulty.MEDIUM;
        DetailLevel detailLevel = raw != null && raw.detailLevel() != null ?
                raw.detailLevel() :
                DetailLevel.STANDARD;

        String focusTopic = null;
        if (raw != null && raw.focusTopic() != null) {
            String trimmed = raw.focusTopic().trim();
            if (!trimmed.isEmpty()) {
                focusTopic = trimmed.length() > 500 ?
                        trimmed.substring(0, 500) :
                        trimmed;
            }
        }

        List<UUID> selectedIds = null;
        int resolvedSourceCount = readyNotebookSourceIds != null ?
                readyNotebookSourceIds.size() :
                0;

        if (raw != null && raw.selectedSourceIds() != null && !raw.selectedSourceIds().isEmpty()) {
            Set<UUID> allowed = readyNotebookSourceIds != null ?
                    Set.copyOf(readyNotebookSourceIds) :
                    Set.of();
            List<UUID> filtered = raw.selectedSourceIds().stream().filter(allowed::contains).distinct().toList();
            if (filtered.isEmpty() && !allowed.isEmpty()) {
                throw new IllegalArgumentException("None of the selected sources are ready");
            }
            resolvedSourceCount = filtered.size();
            if (readyNotebookSourceIds == null || filtered.size() < readyNotebookSourceIds.size()) {
                selectedIds = filtered;
            }
        }

        return switch (type) {
            case MIND_MAP -> {
                int defaultBranches = switch (detailLevel) {
                    case CONCISE -> 4;
                    case STANDARD -> 6;
                    case DETAILED -> 8;
                };
                int branches = raw != null && raw.branchCount() != null ?
                        Math.clamp(raw.branchCount(), 3, 10) :
                        defaultBranches;
                int depth = raw != null && raw.maxDepth() != null ?
                        Math.clamp(raw.maxDepth(), 2, 4) :
                        3;
                yield new GenerationOptions(
                        difficulty,
                        detailLevel,
                        null,
                        depth,
                        branches,
                        null,
                        null,
                        focusTopic,
                        selectedIds,
                        resolvedSourceCount
                );
            }
            case STUDY_GUIDE -> {
                List<String> normalizedSections = filterValidList(
                        raw != null ?
                                raw.sections() :
                                null, VALID_STUDY_GUIDE_SECTIONS
                );
                yield new GenerationOptions(
                        difficulty,
                        detailLevel,
                        null,
                        null,
                        null,
                        null,
                        normalizedSections,
                        focusTopic,
                        selectedIds,
                        resolvedSourceCount
                );
            }
            case FLASHCARDS -> {
                int count = raw != null && raw.itemCount() != null ?
                        Math.clamp(raw.itemCount(), 5, 30) :
                        15;
                yield new GenerationOptions(
                        difficulty,
                        detailLevel,
                        count,
                        null,
                        null,
                        null,
                        null,
                        focusTopic,
                        selectedIds,
                        resolvedSourceCount
                );
            }
            case QUESTIONNAIRE -> {
                int count = raw != null && raw.itemCount() != null ?
                        Math.clamp(raw.itemCount(), 3, 25) :
                        10;
                List<String> types = filterValidList(
                        raw != null ?
                                raw.questionTypes() :
                                null, VALID_QUESTION_TYPES
                );
                yield new GenerationOptions(
                        difficulty,
                        detailLevel,
                        count,
                        null,
                        null,
                        types,
                        null,
                        focusTopic,
                        selectedIds,
                        resolvedSourceCount
                );
            }
        };
    }

    private static List<String> filterValidList(List<String> input, List<String> allowedOrdered) {
        if (input == null || input.isEmpty()) {
            return allowedOrdered;
        }
        Set<String> requested = new LinkedHashSet<>(input);
        List<String> valid = allowedOrdered.stream().filter(requested::contains).toList();
        return valid.isEmpty() ?
                allowedOrdered :
                valid;
    }
}
