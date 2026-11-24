package com.escruta.core.services;

import com.escruta.core.dtos.AgentStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LearningMemoryAgentTools & ReadTools Tests")
class LearningMemoryAgentToolsTest {
    @Mock
    private LearningMemoryService learningMemoryService;

    @Test
    @DisplayName("getLearningMemory queries service and records memory trace step")
    void getLearningMemory_recordsStepAndReturnsMemory() {
        UUID notebookId = UUID.randomUUID();
        AgentTraceRecorder recorder = new AgentTraceRecorder(null, null);
        when(learningMemoryService.queryMemoryForTool(
                notebookId,
                "attention"
        )).thenReturn(new LearningMemoryService.MemoryToolQueryResult(
                "NOTEBOOK LEARNING MEMORY\n- Matching concepts needing review: Scaled self-attention",
                "Matched 1 concept"
        ));

        LearningMemoryReadTools readTools = new LearningMemoryReadTools(notebookId, learningMemoryService, recorder);

        String response = readTools.getLearningMemory("attention", "Check if student knows attention mechanism");

        assertThat(response).contains("Scaled self-attention");
        List<AgentStep> steps = recorder.getStepsSnapshot();
        assertThat(steps).hasSize(1);
        assertThat(steps.getFirst().type()).isEqualTo("memory");
        assertThat(steps.getFirst().title()).isEqualTo("Checking learning memory");
        assertThat(steps.getFirst().detail()).isEqualTo("attention");
        assertThat(steps.getFirst().result()).isEqualTo("Matched 1 concept");
        assertThat(steps.getFirst().status()).isEqualTo("done");
    }

    @Test
    @DisplayName("updateLearningProgress records memory trace step and delegates to service")
    void updateLearningProgress_recordsStepAndDelegates() {
        UUID notebookId = UUID.randomUUID();
        AgentTraceRecorder recorder = new AgentTraceRecorder(null, null);
        LearningMemoryAgentTools tools = new LearningMemoryAgentTools(notebookId, learningMemoryService, recorder);

        String response = tools.updateLearningProgress(
                List.of("Bayes Theorem"),
                List.of("Prior vs Posterior"),
                "Benefits from small numerical tables.",
                "Student correctly derived the denominator but confused prior and likelihood"
        );

        assertThat(response).contains("Learning memory updated").contains("Bayes Theorem");
        verify(learningMemoryService).applyTutorObservation(
                notebookId,
                List.of("Bayes Theorem"),
                List.of("Prior vs Posterior"),
                "Benefits from small numerical tables."
        );

        List<AgentStep> steps = recorder.getStepsSnapshot();
        assertThat(steps).hasSize(1);
        assertThat(steps.getFirst().type()).isEqualTo("memory");
        assertThat(steps.getFirst().status()).isEqualTo("done");
        assertThat(steps.getFirst().result()).isEqualTo("Saved 2 concepts");
    }

    @Test
    @DisplayName("updateLearningProgress does nothing when all fields are empty")
    void updateLearningProgress_ignoresEmptyInput() {
        UUID notebookId = UUID.randomUUID();
        AgentTraceRecorder recorder = new AgentTraceRecorder(null, null);
        LearningMemoryAgentTools tools = new LearningMemoryAgentTools(notebookId, learningMemoryService, recorder);

        String response = tools.updateLearningProgress(List.of(), List.of(), "   ", null);

        assertThat(response).contains("No learning progress changes");
        assertThat(recorder.getStepsSnapshot()).isEmpty();
        verifyNoInteractions(learningMemoryService);
    }
}
