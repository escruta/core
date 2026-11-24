package com.escruta.core.services;

import com.escruta.core.dtos.memory.LearningMemoryDTO;
import com.escruta.core.dtos.memory.LearningMemoryUpdateDTO;
import com.escruta.core.dtos.memory.LearningSignalRequest;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.NotebookLearningMemory;
import com.escruta.core.entities.enums.ProficiencyLevel;
import com.escruta.core.repositories.NotebookLearningMemoryRepository;
import com.escruta.core.repositories.NotebookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LearningMemoryService {
    public static final int MAX_CONCEPTS_PER_LIST = 25;
    public static final int MAX_CONCEPT_LENGTH = 80;
    public static final int MAX_GOAL_LENGTH = 500;
    public static final int MAX_NOTES_LENGTH = 1500;

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final NotebookLearningMemoryRepository memoryRepository;
    private final NotebookRepository notebookRepository;
    private final SseNotificationService sseNotificationService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public LearningMemoryDTO getMemory(UUID notebookId) {
        return memoryRepository
                .findById(notebookId)
                .map(this::toDto)
                .orElseGet(() -> LearningMemoryDTO.empty(notebookId));
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID notebookId) {
        return memoryRepository.findById(notebookId).map(NotebookLearningMemory::isEnabled).orElse(true);
    }

    @Transactional
    public LearningMemoryDTO updateMemory(UUID notebookId, LearningMemoryUpdateDTO update) {
        NotebookLearningMemory memory = getOrCreate(notebookId);

        if (update.enabled() != null) {
            memory.setEnabled(update.enabled());
        }
        if (update.proficiencyLevel() != null) {
            memory.setProficiencyLevel(update.proficiencyLevel());
        }
        if (update.learningGoal() != null) {
            memory.setLearningGoal(normalizeOptionalText(update.learningGoal(), MAX_GOAL_LENGTH));
        }
        if (update.studyNotes() != null) {
            memory.setStudyNotes(normalizeOptionalText(update.studyNotes(), MAX_NOTES_LENGTH));
        }

        List<String> mastered = update.masteredConcepts() != null ?
                normalizeConceptList(update.masteredConcepts()) :
                deserializeConcepts(memory.getMasteredConcepts());
        List<String> struggling = update.strugglingConcepts() != null ?
                normalizeConceptList(update.strugglingConcepts()) :
                deserializeConcepts(memory.getStrugglingConcepts());

        if (update.masteredConcepts() != null || update.strugglingConcepts() != null) {
            if (update.masteredConcepts() != null && update.strugglingConcepts() == null) {
                struggling = removeMatching(struggling, mastered);
            } else {
                mastered = removeMatching(mastered, struggling);
            }
            memory.setMasteredConcepts(serializeConcepts(mastered));
            memory.setStrugglingConcepts(serializeConcepts(struggling));
        }

        NotebookLearningMemory saved = memoryRepository.saveAndFlush(memory);
        LearningMemoryDTO dto = toDto(saved);
        publishAfterCommit(notebookId, dto);
        return dto;
    }

    @Transactional
    public LearningMemoryDTO recordPracticeSignal(UUID notebookId, LearningSignalRequest signal) {
        NotebookLearningMemory memory = memoryRepository.findById(notebookId).orElse(null);
        if (memory != null && !memory.isEnabled()) {
            return toDto(memory);
        }
        if (memory == null) {
            memory = getOrCreate(notebookId);
        }

        if (signal.source() == LearningSignalRequest.SignalSource.FLASHCARD) {
            memory.setFlashcardsReviewed(memory.getFlashcardsReviewed() + 1);
            if (signal.outcome() == LearningSignalRequest.SignalOutcome.MASTERED) {
                memory.setFlashcardsKnown(memory.getFlashcardsKnown() + 1);
            }
        } else if (signal.source() == LearningSignalRequest.SignalSource.QUESTIONNAIRE) {
            memory.setQuizQuestionsAnswered(memory.getQuizQuestionsAnswered() + 1);
            if (signal.outcome() == LearningSignalRequest.SignalOutcome.MASTERED) {
                memory.setQuizQuestionsCorrect(memory.getQuizQuestionsCorrect() + 1);
            }
        }

        String concept = normalizeConcept(signal.topic());
        if (concept != null) {
            List<String> mastered = new ArrayList<>(deserializeConcepts(memory.getMasteredConcepts()));
            List<String> struggling = new ArrayList<>(deserializeConcepts(memory.getStrugglingConcepts()));

            if (signal.outcome() == LearningSignalRequest.SignalOutcome.MASTERED) {
                struggling = removeMatching(struggling, List.of(concept));
                mastered = mergeConcepts(mastered, List.of(concept));
            } else {
                mastered = removeMatching(mastered, List.of(concept));
                struggling = mergeConcepts(struggling, List.of(concept));
            }

            memory.setMasteredConcepts(serializeConcepts(mastered));
            memory.setStrugglingConcepts(serializeConcepts(struggling));
        }

        NotebookLearningMemory saved = memoryRepository.saveAndFlush(memory);
        LearningMemoryDTO dto = toDto(saved);
        publishAfterCommit(notebookId, dto);
        return dto;
    }

    @Transactional
    public LearningMemoryDTO applyTutorObservation(
            UUID notebookId,
            @Nullable List<String> masteredToAdd,
            @Nullable List<String> strugglingToAdd,
            @Nullable String observedNote
    ) {
        NotebookLearningMemory memory = memoryRepository.findById(notebookId).orElse(null);
        if (memory != null && !memory.isEnabled()) {
            return toDto(memory);
        }
        if (memory == null) {
            memory = getOrCreate(notebookId);
        }

        List<String> normalizedMastered = normalizeConceptList(masteredToAdd);
        List<String> normalizedStruggling = normalizeConceptList(strugglingToAdd);

        List<String> mastered = new ArrayList<>(deserializeConcepts(memory.getMasteredConcepts()));
        List<String> struggling = new ArrayList<>(deserializeConcepts(memory.getStrugglingConcepts()));

        if (!normalizedMastered.isEmpty()) {
            struggling = removeMatching(struggling, normalizedMastered);
            mastered = mergeConcepts(mastered, normalizedMastered);
        }
        if (!normalizedStruggling.isEmpty()) {
            mastered = removeMatching(mastered, normalizedStruggling);
            struggling = mergeConcepts(struggling, normalizedStruggling);
        }

        memory.setMasteredConcepts(serializeConcepts(mastered));
        memory.setStrugglingConcepts(serializeConcepts(struggling));

        String cleanNote = normalizeOptionalText(observedNote, 300);
        if (cleanNote != null) {
            memory.setStudyNotes(appendStudyNote(memory.getStudyNotes(), cleanNote));
        }

        NotebookLearningMemory saved = memoryRepository.saveAndFlush(memory);
        LearningMemoryDTO dto = toDto(saved);
        publishAfterCommit(notebookId, dto);
        return dto;
    }

    @Transactional
    public void recordLearningTurn(UUID notebookId) {
        NotebookLearningMemory memory = memoryRepository.findById(notebookId).orElse(null);
        if (memory != null && !memory.isEnabled()) {
            return;
        }
        if (memory == null) {
            if (!notebookRepository.existsById(notebookId)) {
                return;
            }
            memory = getOrCreate(notebookId);
        }
        memory.setLearningTurnsCount(memory.getLearningTurnsCount() + 1);
        NotebookLearningMemory saved = memoryRepository.saveAndFlush(memory);
        publishAfterCommit(notebookId, toDto(saved));
    }

    @Transactional
    public LearningMemoryDTO resetMemory(UUID notebookId) {
        if (!notebookRepository.existsById(notebookId)) {
            throw new NoSuchElementException("Notebook not found");
        }
        boolean currentlyEnabled = memoryRepository
                .findById(notebookId)
                .map(NotebookLearningMemory::isEnabled)
                .orElse(true);

        memoryRepository.deleteById(notebookId);
        memoryRepository.flush();

        LearningMemoryDTO empty = new LearningMemoryDTO(
                notebookId,
                currentlyEnabled,
                ProficiencyLevel.AUTO,
                null,
                List.of(),
                List.of(),
                null,
                new LearningMemoryDTO.PracticeStats(0, 0, 0, 0, 0),
                null
        );
        if (!currentlyEnabled) {
            NotebookLearningMemory disabledRecord = getOrCreate(notebookId);
            disabledRecord.setEnabled(false);
            NotebookLearningMemory saved = memoryRepository.saveAndFlush(disabledRecord);
            empty = toDto(saved);
        }

        publishAfterCommit(notebookId, empty);
        return empty;
    }

    @Transactional(readOnly = true)
    public String buildTutorMemoryHint(UUID notebookId) {
        PromptMemorySnapshot snapshot = loadEnabledSnapshot(notebookId, MAX_NOTES_LENGTH);
        if (snapshot == null || (snapshot.isProfileEmpty() && snapshot.isPracticeEmpty())) {
            return "";
        }

        List<String> summaryParts = new ArrayList<>();
        if (snapshot.level() != ProficiencyLevel.AUTO) {
            summaryParts.add("level: " + snapshot.level().name());
        }
        if (snapshot.goal() != null) {
            summaryParts.add("goal: \"" + truncateForHint(snapshot.goal()) + "\"");
        }
        if (!snapshot.struggling().isEmpty()) {
            summaryParts.add(snapshot.struggling().size() + " concept(s) need review");
        }
        if (!snapshot.mastered().isEmpty()) {
            summaryParts.add(snapshot.mastered().size() + " mastered");
        }
        if (snapshot.notes() != null) {
            summaryParts.add("tutoring notes available");
        }
        if (summaryParts.isEmpty()) {
            summaryParts.addAll(buildPracticeActivityParts(snapshot.memory()));
        }

        return "LEARNING MEMORY STATUS (scoped to this notebook):\n" + "- Active (" + String.join(
                ", ",
                summaryParts
        ) + ").\n" + "- Call `getLearningMemory` (optionally with a topic filter) when tailoring explanations, choosing pacing, or checking what the student already knows or struggles with.";
    }

    @Transactional(readOnly = true)
    public MemoryToolQueryResult queryMemoryForTool(UUID notebookId, @Nullable String topic) {
        NotebookLearningMemory memory = memoryRepository.findById(notebookId).orElse(null);
        if (memory != null && !memory.isEnabled()) {
            return new MemoryToolQueryResult("Learning memory is currently paused for this notebook.", "Memory paused");
        }

        PromptMemorySnapshot snapshot = loadEnabledSnapshot(notebookId, MAX_NOTES_LENGTH);
        if (snapshot == null || (snapshot.isProfileEmpty() && snapshot.isPracticeEmpty())) {
            return new MemoryToolQueryResult(
                    "No learning memory has been recorded for this notebook yet.",
                    "No progress recorded yet"
            );
        }

        String cleanTopic = normalizeOptionalText(topic, 120);
        StringBuilder sb = new StringBuilder();
        sb.append("NOTEBOOK LEARNING MEMORY (pedagogical context only — NEVER cite as a factual source):\n");

        if (snapshot.level() != ProficiencyLevel.AUTO) {
            sb.append("- Target proficiency level: ").append(snapshot.level().name()).append("\n");
        }
        if (snapshot.goal() != null) {
            sb.append("- Learning goal: ").append(snapshot.goal()).append("\n");
        }

        String stepSummary;
        if (cleanTopic != null) {
            List<String> matchedStruggling = filterConceptsByTopic(snapshot.struggling(), cleanTopic);
            List<String> matchedMastered = filterConceptsByTopic(snapshot.mastered(), cleanTopic);
            int matchedCount = matchedStruggling.size() + matchedMastered.size();

            sb.append("- Topic filter: \"").append(cleanTopic).append("\"\n");
            if (!matchedStruggling.isEmpty()) {
                sb
                        .append("- Matching concepts needing review: ")
                        .append(String.join("; ", matchedStruggling))
                        .append("\n");
            }
            if (!matchedMastered.isEmpty()) {
                sb.append("- Matching mastered concepts: ").append(String.join("; ", matchedMastered)).append("\n");
            }
            if (matchedCount == 0) {
                sb.append("- No stored concepts directly matched \"").append(cleanTopic).append("\".\n");
                appendFullConceptLists(sb, snapshot);
            } else {
                List<String> otherStruggling = removeMatching(snapshot.struggling(), matchedStruggling);
                List<String> otherMastered = removeMatching(snapshot.mastered(), matchedMastered);
                if (!otherStruggling.isEmpty()) {
                    sb
                            .append("- Other concepts needing review: ")
                            .append(String.join("; ", otherStruggling.stream().limit(8).toList()))
                            .append("\n");
                }
                if (!otherMastered.isEmpty()) {
                    sb
                            .append("- Other mastered concepts: ")
                            .append(String.join("; ", otherMastered.stream().limit(8).toList()))
                            .append("\n");
                }
            }
            stepSummary = matchedCount > 0 ?
                    "Matched " + matchedCount + " concept" + (matchedCount == 1 ?
                            "" :
                            "s") :
                    buildProfileSummaryLabel(snapshot);
        } else {
            appendFullConceptLists(sb, snapshot);
            stepSummary = buildProfileSummaryLabel(snapshot);
        }

        if (snapshot.notes() != null) {
            sb.append("- Pedagogical notes: ").append(snapshot.notes()).append("\n");
        }

        List<String> practiceParts = buildPracticeActivityParts(snapshot.memory());
        if (!practiceParts.isEmpty()) {
            sb.append("- Practice activity: ").append(String.join(", ", practiceParts)).append("\n");
        }

        return new MemoryToolQueryResult(sb.toString().trim(), stepSummary);
    }

    public record MemoryToolQueryResult(
            String content,
            String stepSummary
    ) {
    }

    private static void appendFullConceptLists(StringBuilder sb, PromptMemorySnapshot snapshot) {
        if (!snapshot.struggling().isEmpty()) {
            sb
                    .append("- Concepts needing reinforcement: ")
                    .append(String.join("; ", snapshot.struggling()))
                    .append("\n");
        }
        if (!snapshot.mastered().isEmpty()) {
            sb.append("- Concepts already understood: ").append(String.join("; ", snapshot.mastered())).append("\n");
        }
    }

    private static String buildProfileSummaryLabel(PromptMemorySnapshot snapshot) {
        List<String> labels = new ArrayList<>();
        if (!snapshot.struggling().isEmpty()) {
            labels.add(snapshot.struggling().size() + " review");
        }
        if (!snapshot.mastered().isEmpty()) {
            labels.add(snapshot.mastered().size() + " mastered");
        }
        if (!labels.isEmpty()) {
            return "Loaded " + String.join(", ", labels);
        }
        if (snapshot.goal() != null || snapshot.level() != ProficiencyLevel.AUTO) {
            return "Loaded study profile";
        }
        if (snapshot.notes() != null) {
            return "Loaded tutoring notes";
        }
        return "Loaded practice stats";
    }

    private static List<String> filterConceptsByTopic(List<String> concepts, String topic) {
        if (concepts.isEmpty()) {
            return List.of();
        }
        String lowerTopic = topic.toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        for (String token : lowerTopic.split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 3) {
                tokens.add(token);
            }
        }
        return findMatchingConcepts(concepts, lowerTopic, tokens);
    }

    private static @NonNull List<String> findMatchingConcepts(
            List<String> concepts,
            String lowerTopic,
            List<String> tokens
    ) {
        List<String> matches = new ArrayList<>();
        for (String concept : concepts) {
            String lowerConcept = concept.toLowerCase(Locale.ROOT);
            if (lowerConcept.contains(lowerTopic) || lowerTopic.contains(lowerConcept)) {
                matches.add(concept);
                continue;
            }
            for (String token : tokens) {
                if (lowerConcept.contains(token)) {
                    matches.add(concept);
                    break;
                }
            }
        }
        return matches;
    }

    private static String truncateForHint(String text) {
        if (text.length() <= 120) {
            return text;
        }
        return text.substring(0, 120 - 3).trim() + "...";
    }

    @Transactional(readOnly = true)
    public String buildNormalModeGuidance(UUID notebookId) {
        NotebookLearningMemory memory = memoryRepository.findById(notebookId).orElse(null);
        if (memory == null || !memory.isEnabled()) {
            return "";
        }

        List<String> lines = new ArrayList<>();
        ProficiencyLevel level = memory.getProficiencyLevel();
        if (level != null && level != ProficiencyLevel.AUTO) {
            lines.add("- Adapt explanation depth to a " + level.name().toLowerCase(Locale.ROOT) + " reader.");
        }
        String goal = normalizeOptionalText(memory.getLearningGoal(), MAX_GOAL_LENGTH);
        if (goal != null) {
            lines.add("- Notebook study goal (context only, do not override source grounding): " + goal);
        }
        return String.join("\n", lines);
    }

    @Transactional(readOnly = true)
    public String buildToolGenerationContext(UUID notebookId) {
        PromptMemorySnapshot snapshot = loadEnabledSnapshot(notebookId, 400);
        if (snapshot == null || snapshot.isProfileEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(
                "- LEARNER MEMORY CONTEXT (adapt emphasis while respecting explicit options/focus and staying strictly grounded in the provided materials):");
        if (snapshot.level() != ProficiencyLevel.AUTO) {
            sb.append("\n  * Learner level: ").append(snapshot.level().name());
        }
        if (snapshot.goal() != null) {
            sb.append("\n  * Learning goal: ").append(snapshot.goal());
        }
        if (!snapshot.struggling().isEmpty()) {
            sb
                    .append("\n  * Concepts needing reinforcement (give extra clarity and practice coverage): ")
                    .append(String.join("; ", snapshot.struggling().stream().limit(10).toList()));
        }
        if (!snapshot.mastered().isEmpty()) {
            sb
                    .append("\n  * Concepts already mastered (avoid spending too much space on basic recall): ")
                    .append(String.join("; ", snapshot.mastered().stream().limit(10).toList()));
        }
        if (snapshot.notes() != null) {
            sb.append("\n  * Study notes: ").append(snapshot.notes());
        }
        return sb.toString();
    }

    private @Nullable PromptMemorySnapshot loadEnabledSnapshot(UUID notebookId, int maxNotesLength) {
        NotebookLearningMemory memory = memoryRepository.findById(notebookId).orElse(null);
        if (memory == null || !memory.isEnabled()) {
            return null;
        }
        List<String> mastered = deserializeConcepts(memory.getMasteredConcepts());
        List<String> struggling = deserializeConcepts(memory.getStrugglingConcepts());
        String goal = normalizeOptionalText(memory.getLearningGoal(), MAX_GOAL_LENGTH);
        String notes = normalizeOptionalText(memory.getStudyNotes(), maxNotesLength);
        ProficiencyLevel level = memory.getProficiencyLevel() != null ?
                memory.getProficiencyLevel() :
                ProficiencyLevel.AUTO;
        return new PromptMemorySnapshot(memory, level, goal, notes, mastered, struggling);
    }

    private static List<String> buildPracticeActivityParts(NotebookLearningMemory memory) {
        List<String> parts = new ArrayList<>();
        if (memory.getFlashcardsReviewed() > 0) {
            parts.add("flashcards " + memory.getFlashcardsKnown() + "/" + memory.getFlashcardsReviewed() + " learned");
        }
        if (memory.getQuizQuestionsAnswered() > 0) {
            parts.add("quiz " + memory.getQuizQuestionsCorrect() + "/" + memory.getQuizQuestionsAnswered() + " correct");
        }
        if (memory.getLearningTurnsCount() > 0) {
            parts.add(memory.getLearningTurnsCount() + " tutoring turn(s)");
        }
        return parts;
    }

    private record PromptMemorySnapshot(
            NotebookLearningMemory memory,
            ProficiencyLevel level,
            @Nullable
            String goal,
            @Nullable
            String notes,
            List<String> mastered,
            List<String> struggling
    ) {
        private boolean isProfileEmpty() {
            return level == ProficiencyLevel.AUTO && goal == null && notes == null && mastered.isEmpty() && struggling.isEmpty();
        }

        private boolean isPracticeEmpty() {
            return memory.getFlashcardsReviewed() == 0 && memory.getQuizQuestionsAnswered() == 0 && memory.getLearningTurnsCount() == 0;
        }
    }

    private NotebookLearningMemory getOrCreate(UUID notebookId) {
        return memoryRepository.findById(notebookId).orElseGet(() -> {
            Notebook notebook = notebookRepository
                    .findById(notebookId)
                    .orElseThrow(() -> new NoSuchElementException("Notebook not found"));
            return new NotebookLearningMemory(notebook);
        });
    }

    private LearningMemoryDTO toDto(NotebookLearningMemory memory) {
        return new LearningMemoryDTO(
                memory.getNotebookId(),
                memory.isEnabled(),
                memory.getProficiencyLevel() != null ?
                        memory.getProficiencyLevel() :
                        ProficiencyLevel.AUTO,
                memory.getLearningGoal(),
                deserializeConcepts(memory.getMasteredConcepts()),
                deserializeConcepts(memory.getStrugglingConcepts()),
                memory.getStudyNotes(),
                new LearningMemoryDTO.PracticeStats(
                        memory.getFlashcardsReviewed(),
                        memory.getFlashcardsKnown(),
                        memory.getQuizQuestionsAnswered(),
                        memory.getQuizQuestionsCorrect(),
                        memory.getLearningTurnsCount()
                ),
                memory.getUpdatedAt() != null ?
                        memory.getUpdatedAt().toInstant() :
                        null
        );
    }

    private void publishAfterCommit(UUID notebookId, LearningMemoryDTO dto) {
        UUID ownerId = notebookRepository.findOwnerId(notebookId);
        if (ownerId == null) {
            return;
        }
        Runnable publish = () -> sseNotificationService.publish(ownerId, "learningMemory.updated", dto);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    private List<String> deserializeConcepts(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(json, STRING_LIST);
            return parsed != null ?
                    parsed :
                    List.of();
        } catch (Exception e) {
            log.warn("Could not deserialize learning memory concepts: {}", e.getMessage());
            return List.of();
        }
    }

    private @Nullable String serializeConcepts(List<String> concepts) {
        if (concepts == null || concepts.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(concepts);
        } catch (Exception e) {
            log.warn("Could not serialize learning memory concepts: {}", e.getMessage());
            return null;
        }
    }

    static @Nullable String normalizeConcept(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw
                .strip()
                .replaceAll("^[-*•\\s]+", "")
                .replaceAll("^[\"']|[\"']$", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > MAX_CONCEPT_LENGTH) {
            int cut = cleaned.lastIndexOf(' ', MAX_CONCEPT_LENGTH - 3);
            if (cut < MAX_CONCEPT_LENGTH / 2) {
                cut = MAX_CONCEPT_LENGTH - 3;
            }
            cleaned = cleaned.substring(0, cut).trim() + "...";
        }
        return cleaned;
    }

    static List<String> normalizeConceptList(@Nullable List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Map<String, String> deduped = new LinkedHashMap<>();
        for (String item : raw) {
            String clean = normalizeConcept(item);
            if (clean != null) {
                deduped.putIfAbsent(clean.toLowerCase(Locale.ROOT), clean);
            }
            if (deduped.size() >= MAX_CONCEPTS_PER_LIST) {
                break;
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private static List<String> mergeConcepts(List<String> existing, List<String> incoming) {
        Map<String, String> merged = new LinkedHashMap<>();
        for (String item : existing) {
            String clean = normalizeConcept(item);
            if (clean != null) {
                merged.put(clean.toLowerCase(Locale.ROOT), clean);
            }
        }
        for (String item : incoming) {
            String clean = normalizeConcept(item);
            if (clean != null) {
                String key = clean.toLowerCase(Locale.ROOT);
                merged.remove(key);
                merged.put(key, clean);
            }
        }
        List<String> values = new ArrayList<>(merged.values());
        if (values.size() > MAX_CONCEPTS_PER_LIST) {
            return new ArrayList<>(values.subList(values.size() - MAX_CONCEPTS_PER_LIST, values.size()));
        }
        return values;
    }

    private static List<String> removeMatching(List<String> list, List<String> toRemove) {
        if (list.isEmpty() || toRemove.isEmpty()) {
            return list;
        }
        var removeKeys = toRemove
                .stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        return list.stream().filter(item -> !removeKeys.contains(item.toLowerCase(Locale.ROOT))).toList();
    }

    private static @Nullable String normalizeOptionalText(@Nullable String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            return trimmed.substring(0, maxLength).trim();
        }
        return trimmed;
    }

    private static String appendStudyNote(@Nullable String existing, String newNote) {
        if (existing == null || existing.isBlank()) {
            return newNote;
        }
        String lowerExisting = existing.toLowerCase(Locale.ROOT);
        if (lowerExisting.contains(newNote.toLowerCase(Locale.ROOT))) {
            return existing;
        }
        String combined = existing.strip() + "\n" + newNote;
        if (combined.length() <= MAX_NOTES_LENGTH) {
            return combined;
        }
        return combined.substring(combined.length() - MAX_NOTES_LENGTH).strip();
    }
}
