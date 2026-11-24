package com.escruta.core.controllers;

import com.escruta.core.dtos.memory.LearningMemoryDTO;
import com.escruta.core.dtos.memory.LearningMemoryUpdateDTO;
import com.escruta.core.dtos.memory.LearningSignalRequest;
import com.escruta.core.entities.enums.ProficiencyLevel;
import com.escruta.core.services.LearningMemoryService;
import com.escruta.core.services.NotebookOwnershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("LearningMemoryController Tests")
class LearningMemoryControllerTest {
    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @MockitoBean
    private LearningMemoryService learningMemoryService;

    @MockitoBean
    private NotebookOwnershipService notebookOwnershipService;

    private UUID notebookId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        notebookId = UUID.randomUUID();
        when(notebookOwnershipService.isUserNotebookOwner(notebookId)).thenReturn(true);
    }

    @Test
    @WithMockUser(username = "learner@example.com")
    @DisplayName("GET /learning-memory returns current notebook learning memory")
    void getLearningMemory_returnsMemory() throws Exception {
        LearningMemoryDTO dto = new LearningMemoryDTO(
                notebookId,
                true,
                ProficiencyLevel.INTERMEDIATE,
                "Understand quantum gates",
                List.of("Qubits"),
                List.of("Entanglement"),
                "Prefers matrix examples",
                new LearningMemoryDTO.PracticeStats(5, 4, 10, 8, 3),
                Instant.now()
        );
        when(learningMemoryService.getMemory(notebookId)).thenReturn(dto);

        mockMvc
                .perform(get("/notebooks/{notebookId}/learning-memory", notebookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.proficiencyLevel").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.learningGoal").value("Understand quantum gates"))
                .andExpect(jsonPath("$.masteredConcepts[0]").value("Qubits"))
                .andExpect(jsonPath("$.strugglingConcepts[0]").value("Entanglement"))
                .andExpect(jsonPath("$.stats.quizQuestionsCorrect").value(8));
    }

    @Test
    @WithMockUser(username = "learner@example.com")
    @DisplayName("PUT /learning-memory updates preferences and concepts")
    void updateLearningMemory_updatesProfile() throws Exception {
        LearningMemoryUpdateDTO req = new LearningMemoryUpdateDTO(
                true,
                ProficiencyLevel.ADVANCED,
                "Review proofs",
                List.of("Bell Inequality"),
                List.of(),
                "Skip introductory definitions"
        );
        LearningMemoryDTO updated = new LearningMemoryDTO(
                notebookId,
                true,
                ProficiencyLevel.ADVANCED,
                "Review proofs",
                List.of("Bell Inequality"),
                List.of(),
                "Skip introductory definitions",
                new LearningMemoryDTO.PracticeStats(0, 0, 0, 0, 0),
                Instant.now()
        );
        when(learningMemoryService.updateMemory(eq(notebookId), any())).thenReturn(updated);

        mockMvc
                .perform(put("/notebooks/{notebookId}/learning-memory", notebookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proficiencyLevel").value("ADVANCED"))
                .andExpect(jsonPath("$.masteredConcepts[0]").value("Bell Inequality"));
    }

    @Test
    @WithMockUser(username = "learner@example.com")
    @DisplayName("POST /learning-memory/signals records practice signal")
    void recordSignal_recordsFlashcardSignal() throws Exception {
        LearningSignalRequest req = new LearningSignalRequest(
                LearningSignalRequest.SignalSource.FLASHCARD,
                LearningSignalRequest.SignalOutcome.STRUGGLING,
                "Hadamard gate",
                null
        );
        LearningMemoryDTO updated = new LearningMemoryDTO(
                notebookId,
                true,
                ProficiencyLevel.AUTO,
                null,
                List.of(),
                List.of("Hadamard gate"),
                null,
                new LearningMemoryDTO.PracticeStats(1, 0, 0, 0, 0),
                Instant.now()
        );
        when(learningMemoryService.recordPracticeSignal(eq(notebookId), any())).thenReturn(updated);

        mockMvc
                .perform(post("/notebooks/{notebookId}/learning-memory/signals", notebookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strugglingConcepts[0]").value("Hadamard gate"))
                .andExpect(jsonPath("$.stats.flashcardsReviewed").value(1));
    }

    @Test
    @WithMockUser(username = "learner@example.com")
    @DisplayName("DELETE /learning-memory resets learning progress")
    void resetLearningMemory_returnsEmptyProfile() throws Exception {
        when(learningMemoryService.resetMemory(notebookId)).thenReturn(LearningMemoryDTO.empty(notebookId));

        mockMvc
                .perform(delete("/notebooks/{notebookId}/learning-memory", notebookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masteredConcepts").isEmpty())
                .andExpect(jsonPath("$.strugglingConcepts").isEmpty());
    }

    @Test
    @WithMockUser(username = "learner@example.com")
    @DisplayName("Returns 403 when user does not own the notebook")
    void accessLearningMemory_returns403WhenNotOwner() throws Exception {
        UUID foreignNotebookId = UUID.randomUUID();
        when(notebookOwnershipService.isUserNotebookOwner(foreignNotebookId)).thenReturn(false);

        mockMvc
                .perform(get("/notebooks/{notebookId}/learning-memory", foreignNotebookId))
                .andExpect(status().isForbidden());
    }
}
