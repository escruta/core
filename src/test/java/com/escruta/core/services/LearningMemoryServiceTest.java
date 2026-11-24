package com.escruta.core.services;

import com.escruta.core.dtos.memory.LearningMemoryDTO;
import com.escruta.core.dtos.memory.LearningMemoryUpdateDTO;
import com.escruta.core.dtos.memory.LearningSignalRequest;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.NotebookLearningMemory;
import com.escruta.core.entities.enums.ProficiencyLevel;
import com.escruta.core.repositories.NotebookLearningMemoryRepository;
import com.escruta.core.repositories.NotebookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LearningMemoryService Tests")
class LearningMemoryServiceTest {
    @Mock
    private NotebookLearningMemoryRepository memoryRepository;
    @Mock
    private NotebookRepository notebookRepository;
    @Mock
    private SseNotificationService sseNotificationService;

    private ObjectMapper objectMapper;
    private LearningMemoryService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new LearningMemoryService(memoryRepository, notebookRepository, sseNotificationService, objectMapper);
    }

    private Notebook notebook(UUID notebookId) {
        Notebook nb = new Notebook();
        nb.setId(notebookId);
        nb.setTitle("Deep Learning");
        return nb;
    }

    private NotebookLearningMemory existingMemory(UUID notebookId) {
        Notebook nb = notebook(notebookId);
        NotebookLearningMemory memory = new NotebookLearningMemory(nb);
        memory.setNotebookId(notebookId);
        return memory;
    }

    @Test
    @DisplayName("getMemory returns default enabled empty profile when no record exists")
    void getMemory_returnsEmptyDefault() {
        UUID notebookId = UUID.randomUUID();
        when(memoryRepository.findById(notebookId)).thenReturn(Optional.empty());

        LearningMemoryDTO dto = service.getMemory(notebookId);

        assertThat(dto.notebookId()).isEqualTo(notebookId);
        assertThat(dto.enabled()).isTrue();
        assertThat(dto.proficiencyLevel()).isEqualTo(ProficiencyLevel.AUTO);
        assertThat(dto.masteredConcepts()).isEmpty();
        assertThat(dto.strugglingConcepts()).isEmpty();
        assertThat(dto.stats().flashcardsReviewed()).isZero();
    }

    @Test
    @DisplayName("updateMemory normalizes concepts, avoids cross-list duplicates, and publishes SSE")
    void updateMemory_normalizesAndPublishes() {
        UUID notebookId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Notebook nb = notebook(notebookId);

        when(memoryRepository.findById(notebookId)).thenReturn(Optional.empty());
        when(notebookRepository.findById(notebookId)).thenReturn(Optional.of(nb));
        when(notebookRepository.findOwnerId(notebookId)).thenReturn(ownerId);
        when(memoryRepository.saveAndFlush(any(NotebookLearningMemory.class))).thenAnswer(inv -> {
            NotebookLearningMemory m = inv.getArgument(0);
            m.setNotebookId(notebookId);
            return m;
        });

        LearningMemoryUpdateDTO update = new LearningMemoryUpdateDTO(
                true,
                ProficiencyLevel.BEGINNER,
                "  Prepare for midterm exam  ",
                List.of("Gradient Descent", "gradient descent", "Backpropagation"),
                List.of("- Backpropagation", "Vanishing Gradients"),
                "Prefers concrete numeric walkthroughs"
        );

        LearningMemoryDTO result = service.updateMemory(notebookId, update);

        assertThat(result.proficiencyLevel()).isEqualTo(ProficiencyLevel.BEGINNER);
        assertThat(result.learningGoal()).isEqualTo("Prepare for midterm exam");
        assertThat(result.masteredConcepts()).containsExactly("Gradient Descent");
        assertThat(result.strugglingConcepts()).containsExactly("Backpropagation", "Vanishing Gradients");
        assertThat(result.studyNotes()).isEqualTo("Prefers concrete numeric walkthroughs");
        verify(sseNotificationService).publish(eq(ownerId), eq("learningMemory.updated"), any(LearningMemoryDTO.class));
    }

    @Test
    @DisplayName("recordPracticeSignal tracks flashcard/quiz metrics and moves concept between lists")
    void recordPracticeSignal_movesConceptsAndIncrementsStats() {
        UUID notebookId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        NotebookLearningMemory mem = existingMemory(notebookId);
        mem.setStrugglingConcepts(objectMapper.writeValueAsString(List.of("Chain Rule")));

        when(memoryRepository.findById(notebookId)).thenReturn(Optional.of(mem));
        when(notebookRepository.findOwnerId(notebookId)).thenReturn(ownerId);
        when(memoryRepository.saveAndFlush(any(NotebookLearningMemory.class))).thenAnswer(inv -> inv.getArgument(0));

        LearningMemoryDTO afterMastered = service.recordPracticeSignal(
                notebookId, new LearningSignalRequest(
                        LearningSignalRequest.SignalSource.QUESTIONNAIRE,
                        LearningSignalRequest.SignalOutcome.MASTERED,
                        "chain rule",
                        null
                )
        );

        assertThat(afterMastered.stats().quizQuestionsAnswered()).isEqualTo(1);
        assertThat(afterMastered.stats().quizQuestionsCorrect()).isEqualTo(1);
        assertThat(afterMastered.strugglingConcepts()).isEmpty();
        assertThat(afterMastered.masteredConcepts()).containsExactly("chain rule");

        LearningMemoryDTO afterStruggle = service.recordPracticeSignal(
                notebookId, new LearningSignalRequest(
                        LearningSignalRequest.SignalSource.FLASHCARD,
                        LearningSignalRequest.SignalOutcome.STRUGGLING,
                        "Chain Rule",
                        null
                )
        );

        assertThat(afterStruggle.stats().flashcardsReviewed()).isEqualTo(1);
        assertThat(afterStruggle.stats().flashcardsKnown()).isZero();
        assertThat(afterStruggle.masteredConcepts()).isEmpty();
        assertThat(afterStruggle.strugglingConcepts()).containsExactly("Chain Rule");
    }

    @Test
    @DisplayName("recordPracticeSignal does nothing when learning memory is disabled")
    void recordPracticeSignal_ignoresWhenDisabled() {
        UUID notebookId = UUID.randomUUID();
        NotebookLearningMemory mem = existingMemory(notebookId);
        mem.setEnabled(false);

        when(memoryRepository.findById(notebookId)).thenReturn(Optional.of(mem));

        LearningMemoryDTO dto = service.recordPracticeSignal(
                notebookId, new LearningSignalRequest(
                        LearningSignalRequest.SignalSource.FLASHCARD,
                        LearningSignalRequest.SignalOutcome.MASTERED,
                        "Eigenvalues",
                        null
                )
        );

        assertThat(dto.enabled()).isFalse();
        assertThat(dto.masteredConcepts()).isEmpty();
        verify(memoryRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("buildTutorMemoryHint, queryMemoryForTool, and buildToolGenerationContext respect enabled flag")
    void promptBuildersAndToolQuery_formatOnlyWhenEnabledAndPopulated() {
        UUID notebookId = UUID.randomUUID();
        NotebookLearningMemory mem = existingMemory(notebookId);
        mem.setProficiencyLevel(ProficiencyLevel.INTERMEDIATE);
        mem.setLearningGoal("Master transformer attention math");
        mem.setMasteredConcepts(objectMapper.writeValueAsString(List.of("Dot product", "Matrix multiplication")));
        mem.setStrugglingConcepts(objectMapper.writeValueAsString(List.of(
                "Scaled self-attention",
                "Positional encoding"
        )));
        mem.setStudyNotes("Likes matrix dimension checks.");
        mem.setQuizQuestionsAnswered(4);
        mem.setQuizQuestionsCorrect(3);

        when(memoryRepository.findById(notebookId)).thenReturn(Optional.of(mem));

        String tutorHint = service.buildTutorMemoryHint(notebookId);
        assertThat(tutorHint)
                .contains("LEARNING MEMORY STATUS")
                .contains("INTERMEDIATE")
                .contains("Master transformer attention math")
                .contains("2 concept(s) need review")
                .contains("2 mastered")
                .contains("getLearningMemory")
                .doesNotContain("Dot product");

        LearningMemoryService.MemoryToolQueryResult fullQuery = service.queryMemoryForTool(notebookId, null);
        assertThat(fullQuery.content())
                .contains("NOTEBOOK LEARNING MEMORY")
                .contains("Dot product")
                .contains("Scaled self-attention")
                .contains("quiz 3/4 correct")
                .contains("NEVER cite as a factual source");
        assertThat(fullQuery.stepSummary()).isEqualTo("Loaded 2 review, 2 mastered");

        LearningMemoryService.MemoryToolQueryResult topicQuery = service.queryMemoryForTool(notebookId, "attention");
        assertThat(topicQuery.content())
                .contains("Matching concepts needing review: Scaled self-attention")
                .contains("Other concepts needing review: Positional encoding");
        assertThat(topicQuery.stepSummary()).isEqualTo("Matched 1 concept");

        String toolPrompt = service.buildToolGenerationContext(notebookId);
        assertThat(toolPrompt)
                .contains("LEARNER MEMORY CONTEXT")
                .contains("Scaled self-attention")
                .contains("Dot product");

        mem.setEnabled(false);
        assertThat(service.buildTutorMemoryHint(notebookId)).isEmpty();
        assertThat(service.queryMemoryForTool(notebookId, null).content()).contains("currently paused");
        assertThat(service.buildToolGenerationContext(notebookId)).isEmpty();
        assertThat(service.buildNormalModeGuidance(notebookId)).isEmpty();
    }

    @Test
    @DisplayName("applyTutorObservation merges concepts and deduplicates study notes")
    void applyTutorObservation_mergesAndAppendsNotes() {
        UUID notebookId = UUID.randomUUID();
        NotebookLearningMemory mem = existingMemory(notebookId);
        mem.setStudyNotes("Responds well to visual analogies.");

        when(memoryRepository.findById(notebookId)).thenReturn(Optional.of(mem));
        when(notebookRepository.findOwnerId(notebookId)).thenReturn(null);
        when(memoryRepository.saveAndFlush(any(NotebookLearningMemory.class))).thenAnswer(inv -> inv.getArgument(0));

        LearningMemoryDTO updated = service.applyTutorObservation(
                notebookId,
                List.of("Tokenization"),
                List.of("Positional encoding"),
                "Needs slower step-by-step derivations."
        );

        assertThat(updated.masteredConcepts()).containsExactly("Tokenization");
        assertThat(updated.strugglingConcepts()).containsExactly("Positional encoding");
        assertThat(updated.studyNotes())
                .contains("Responds well to visual analogies.")
                .contains("Needs slower step-by-step derivations.");
    }
}
