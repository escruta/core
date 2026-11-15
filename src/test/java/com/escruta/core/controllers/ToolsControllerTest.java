package com.escruta.core.controllers;

import com.escruta.core.dtos.tools.GenerationJobResponse;
import com.escruta.core.dtos.tools.GenerationJobUpdateDTO;
import com.escruta.core.dtos.tools.GenerationOptions;
import com.escruta.core.dtos.tools.GenerationOptions.DetailLevel;
import com.escruta.core.dtos.tools.GenerationOptions.Difficulty;
import com.escruta.core.dtos.tools.GenerationRequest;
import com.escruta.core.entities.GenerationJob;
import com.escruta.core.entities.GenerationJob.JobStatus;
import com.escruta.core.entities.GenerationJob.JobType;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.User;
import com.escruta.core.services.NotebookOwnershipService;
import com.escruta.core.services.RateLimitService;
import com.escruta.core.services.ToolsGenerationService;
import com.escruta.core.services.UserService;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ToolsController Tests")
class ToolsControllerTest {
    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @MockitoBean
    private ToolsGenerationService generationService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private NotebookOwnershipService notebookOwnershipService;

    private User testUser;
    private Notebook testNotebook;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("tools@example.com");
        testUser.setName("Tools Tester");

        testNotebook = new Notebook();
        testNotebook.setId(UUID.randomUUID());
        testNotebook.setUser(testUser);
        testNotebook.setTitle("Physics Notebook");

        when(notebookOwnershipService.isUserNotebookOwner(testNotebook.getId())).thenReturn(true);
    }

    @Test
    @WithMockUser(username = "tools@example.com")
    @DisplayName("POST /generate accepts customized options and returns 202 with job details")
    void startGeneration_shouldAcceptCustomizedMetrics() throws Exception {
        UUID jobId = UUID.randomUUID();
        GenerationOptions options = new GenerationOptions(
                Difficulty.HARD,
                DetailLevel.DETAILED,
                20,
                null,
                null,
                List.of("multiple_choice", "short_answer"),
                null,
                "Quantum tunneling",
                null,
                3
        );
        GenerationRequest request = new GenerationRequest(JobType.QUESTIONNAIRE, "Quantum Tunneling Quiz", options);

        GenerationJob job = new GenerationJob(
                testNotebook,
                testUser,
                JobType.QUESTIONNAIRE,
                "Quantum Tunneling Quiz",
                true,
                objectMapper.writeValueAsString(options)
        );
        job.setId(jobId);

        GenerationJobResponse jobResponse = new GenerationJobResponse(
                jobId,
                testNotebook.getId(),
                JobType.QUESTIONNAIRE,
                "Quantum Tunneling Quiz",
                JobStatus.PENDING,
                options,
                null,
                null,
                Instant.now(),
                Instant.now(),
                null
        );

        when(userService.getCurrentUser()).thenReturn(testUser);
        when(generationService.createAndDispatchJob(eq(testNotebook.getId()), eq(testUser), any())).thenReturn(job);
        when(generationService.toResponse(job)).thenReturn(jobResponse);

        mockMvc
                .perform(post("/notebooks/{notebookId}/tools/generate", testNotebook.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.job.title").value("Quantum Tunneling Quiz"))
                .andExpect(jsonPath("$.job.options.difficulty").value("HARD"))
                .andExpect(jsonPath("$.job.options.itemCount").value(20))
                .andExpect(jsonPath("$.job.options.focusTopic").value("Quantum tunneling"));

        verify(rateLimitService).checkChatRequest(testUser.getId());
    }

    @Test
    @WithMockUser(username = "tools@example.com")
    @DisplayName("GET /jobs supports type filter, search, and sort parameters")
    void getAllJobs_shouldSupportFiltersAndSort() throws Exception {
        GenerationJob j1 = new GenerationJob(testNotebook, testUser, JobType.FLASHCARDS, "Deck Alpha", true, null);
        j1.setId(UUID.randomUUID());
        GenerationJob j2 = new GenerationJob(testNotebook, testUser, JobType.FLASHCARDS, "Deck Beta", true, null);
        j2.setId(UUID.randomUUID());

        when(userService.getCurrentUser()).thenReturn(testUser);
        when(generationService.getJobsForNotebook(
                testNotebook.getId(),
                testUser.getId(),
                JobType.FLASHCARDS,
                "Deck",
                "Alphabetical",
                50,
                0
        )).thenReturn(List.of(j1, j2));
        when(generationService.toResponse(j1)).thenReturn(GenerationJobResponse.from(j1));
        when(generationService.toResponse(j2)).thenReturn(GenerationJobResponse.from(j2));

        mockMvc
                .perform(get("/notebooks/{notebookId}/tools/jobs", testNotebook.getId())
                        .param("type", "FLASHCARDS")
                        .param("search", "Deck")
                        .param("sort", "Alphabetical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Deck Alpha"))
                .andExpect(jsonPath("$[1].title").value("Deck Beta"));
    }

    @Test
    @WithMockUser(username = "tools@example.com")
    @DisplayName("PATCH /jobs/{jobId} renames a generation")
    void renameJob_shouldUpdateTitle() throws Exception {
        UUID jobId = UUID.randomUUID();
        GenerationJob renamed = new GenerationJob(
                testNotebook,
                testUser,
                JobType.MIND_MAP,
                "Renamed Mind Map",
                true,
                null
        );
        renamed.setId(jobId);

        when(userService.getCurrentUser()).thenReturn(testUser);
        when(generationService.renameJob(testNotebook.getId(), jobId, testUser.getId(), "Renamed Mind Map")).thenReturn(
                Optional.of(renamed));
        when(generationService.toResponse(renamed)).thenReturn(GenerationJobResponse.from(renamed));

        mockMvc
                .perform(patch("/notebooks/{notebookId}/tools/jobs/{jobId}", testNotebook.getId(), jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GenerationJobUpdateDTO("Renamed Mind Map"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed Mind Map"));
    }

    @Test
    @WithMockUser(username = "tools@example.com")
    @DisplayName("POST /jobs/{jobId}/retry retries a failed generation")
    void retryJob_shouldReturnAccepted() throws Exception {
        UUID jobId = UUID.randomUUID();
        GenerationJob retried = new GenerationJob(
                testNotebook,
                testUser,
                JobType.STUDY_GUIDE,
                "Retried Guide",
                false,
                null
        );
        retried.setId(jobId);

        when(userService.getCurrentUser()).thenReturn(testUser);
        when(generationService.retryJob(eq(testNotebook.getId()), eq(jobId), eq(testUser), any())).thenReturn(retried);
        when(generationService.toResponse(retried)).thenReturn(GenerationJobResponse.from(retried));

        mockMvc
                .perform(post(
                        "/notebooks/{notebookId}/tools/jobs/{jobId}/retry",
                        testNotebook.getId(),
                        jobId
                ).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "tools@example.com")
    @DisplayName("DELETE /jobs/{jobId} deletes a generation and returns 204")
    void deleteJob_shouldReturnNoContent() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(userService.getCurrentUser()).thenReturn(testUser);
        when(generationService.deleteJob(testNotebook.getId(), jobId, testUser.getId())).thenReturn(true);

        mockMvc
                .perform(delete("/notebooks/{notebookId}/tools/jobs/{jobId}", testNotebook.getId(), jobId))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "tools@example.com")
    @DisplayName("Returns 403 when user does not own the notebook")
    void accessTools_shouldReturn403WhenNotOwner() throws Exception {
        UUID foreignNotebookId = UUID.randomUUID();
        when(notebookOwnershipService.isUserNotebookOwner(foreignNotebookId)).thenReturn(false);

        mockMvc.perform(get("/notebooks/{notebookId}/tools/jobs", foreignNotebookId)).andExpect(status().isForbidden());
    }
}
