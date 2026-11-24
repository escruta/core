package com.escruta.core.integration;

import com.escruta.core.dtos.memory.LearningMemoryDTO;
import com.escruta.core.dtos.memory.LearningMemoryUpdateDTO;
import com.escruta.core.dtos.memory.LearningSignalRequest;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.User;
import com.escruta.core.entities.enums.ProficiencyLevel;
import com.escruta.core.repositories.NotebookLearningMemoryRepository;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.UserRepository;
import com.escruta.core.services.LearningMemoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Learning memory integration tests")
class LearningMemoryIntegrationTest {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotebookRepository notebookRepository;
    @Autowired
    private NotebookLearningMemoryRepository memoryRepository;
    @Autowired
    private LearningMemoryService learningMemoryService;

    @Test
    @DisplayName("Persists learning memory lifecycle and cascades on notebook deletion")
    void learningMemory_fullPersistenceLifecycle() {
        User user = new User();
        user.setName("Memory Integration Tester");
        user.setEmail("learning-memory-" + UUID.randomUUID() + "@example.com");
        user = userRepository.save(user);

        Notebook notebook = new Notebook();
        notebook.setUser(user);
        notebook.setTitle("Linear Algebra");
        notebook = notebookRepository.save(notebook);
        UUID notebookId = notebook.getId();

        LearningMemoryDTO initial = learningMemoryService.getMemory(notebookId);
        assertThat(initial.enabled()).isTrue();
        assertThat(initial.proficiencyLevel()).isEqualTo(ProficiencyLevel.AUTO);
        assertThat(memoryRepository.existsById(notebookId)).isFalse();

        LearningMemoryDTO updated = learningMemoryService.updateMemory(
                notebookId, new LearningMemoryUpdateDTO(
                        true,
                        ProficiencyLevel.INTERMEDIATE,
                        "Prepare for linear algebra final",
                        List.of("Vector spaces"),
                        List.of("Eigenvalues"),
                        "Prefers geometric intuition first"
                )
        );
        assertThat(updated.proficiencyLevel()).isEqualTo(ProficiencyLevel.INTERMEDIATE);
        assertThat(updated.masteredConcepts()).containsExactly("Vector spaces");
        assertThat(updated.strugglingConcepts()).containsExactly("Eigenvalues");

        LearningMemoryDTO afterQuiz = learningMemoryService.recordPracticeSignal(
                notebookId, new LearningSignalRequest(
                        LearningSignalRequest.SignalSource.QUESTIONNAIRE,
                        LearningSignalRequest.SignalOutcome.MASTERED,
                        "Eigenvalues",
                        null
                )
        );
        assertThat(afterQuiz.stats().quizQuestionsAnswered()).isEqualTo(1);
        assertThat(afterQuiz.stats().quizQuestionsCorrect()).isEqualTo(1);
        assertThat(afterQuiz.strugglingConcepts()).isEmpty();
        assertThat(afterQuiz.masteredConcepts()).containsExactly("Vector spaces", "Eigenvalues");

        learningMemoryService.recordLearningTurn(notebookId);
        LearningMemoryDTO afterTurn = learningMemoryService.getMemory(notebookId);
        assertThat(afterTurn.stats().learningTurnsCount()).isEqualTo(1);

        LearningMemoryDTO afterReset = learningMemoryService.resetMemory(notebookId);
        assertThat(afterReset.masteredConcepts()).isEmpty();
        assertThat(afterReset.stats().quizQuestionsAnswered()).isZero();
        assertThat(memoryRepository.existsById(notebookId)).isFalse();

        learningMemoryService.applyTutorObservation(notebookId, List.of("Determinants"), List.of(), null);
        assertThat(memoryRepository.existsById(notebookId)).isTrue();

        notebookRepository.deleteById(notebookId);
        notebookRepository.flush();
        assertThat(memoryRepository.existsById(notebookId)).isFalse();
    }
}
