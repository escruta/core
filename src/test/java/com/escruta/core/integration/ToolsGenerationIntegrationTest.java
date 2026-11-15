package com.escruta.core.integration;

import com.escruta.core.dtos.tools.FlashcardsResponse;
import com.escruta.core.dtos.tools.GenerationJobResponse;
import com.escruta.core.dtos.tools.GenerationOptions;
import com.escruta.core.dtos.tools.GenerationOptions.DetailLevel;
import com.escruta.core.dtos.tools.GenerationOptions.Difficulty;
import com.escruta.core.dtos.tools.GenerationRequest;
import com.escruta.core.entities.GenerationJob;
import com.escruta.core.entities.GenerationJob.JobStatus;
import com.escruta.core.entities.GenerationJob.JobType;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.Source;
import com.escruta.core.entities.User;
import com.escruta.core.entities.enums.SourceStatus;
import com.escruta.core.entities.enums.SourceType;
import com.escruta.core.repositories.GenerationJobRepository;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.SourceRepository;
import com.escruta.core.repositories.UserRepository;
import com.escruta.core.services.ToolsGenerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Tools multiple generations & metrics integration tests")
class ToolsGenerationIntegrationTest {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotebookRepository notebookRepository;
    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private GenerationJobRepository generationJobRepository;
    @Autowired
    private ToolsGenerationService toolsGenerationService;
    @Autowired
    private ChatModel chatModel;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Multiple customized generations of the same tool persist, complete asynchronously, filter, rename, and delete")
    void multipleGenerationsAndMetrics_fullLifecycle() throws Exception {
        User user = new User();
        user.setName("Studio User");
        user.setEmail("studio-" + UUID.randomUUID() + "@example.com");
        user = userRepository.save(user);

        Notebook notebook = new Notebook();
        notebook.setUser(user);
        notebook.setTitle("Molecular Biology");
        notebook.setIcon("🧬");
        notebook = notebookRepository.save(notebook);

        Source s1 = new Source();
        s1.setNotebook(notebook);
        s1.setTitle("DNA Replication");
        s1.setContent(
                "DNA polymerase synthesizes the leading strand continuously and lagging strand via Okazaki fragments.");
        s1.setStatus(SourceStatus.READY);
        s1.setType(SourceType.TEXT);
        s1 = sourceRepository.save(s1);

        Source s2 = new Source();
        s2.setNotebook(notebook);
        s2.setTitle("RNA Transcription");
        s2.setContent("RNA polymerase binds to the promoter region and synthesizes mRNA from the template DNA strand.");
        s2.setStatus(SourceStatus.READY);
        s2.setType(SourceType.TEXT);
        s2 = sourceRepository.save(s2);

        FlashcardsResponse mockDeck1 = new FlashcardsResponse(
                "DNA Replication Basics",
                List.of(
                        new FlashcardsResponse.Flashcard("What synthesizes DNA?", "DNA polymerase"),
                        new FlashcardsResponse.Flashcard("What are lagging fragments called?", "Okazaki fragments")
                )
        );
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(
                objectMapper.writeValueAsString(mockDeck1))))));

        GenerationOptions easySubsetMetrics = new GenerationOptions(
                Difficulty.EASY,
                DetailLevel.CONCISE,
                8,
                null,
                null,
                null,
                null,
                "Focus on Okazaki fragments",
                List.of(s1.getId()),
                null
        );

        GenerationOptions hardAllSourcesMetrics = new GenerationOptions(
                Difficulty.HARD,
                DetailLevel.DETAILED,
                20,
                null,
                null,
                null,
                null,
                "Compare replication vs transcription",
                List.of(s1.getId(), s2.getId()),
                null
        );

        GenerationJob firstGen = toolsGenerationService.createAndDispatchJob(
                notebook.getId(),
                user,
                new GenerationRequest(JobType.FLASHCARDS, null, easySubsetMetrics)
        );

        GenerationJob secondGen = toolsGenerationService.createAndDispatchJob(
                notebook.getId(),
                user,
                new GenerationRequest(JobType.FLASHCARDS, "Comprehensive Exam Deck", hardAllSourcesMetrics)
        );

        assertThat(firstGen.getId()).isNotEqualTo(secondGen.getId());

        awaitCompletion(firstGen.getId());
        awaitCompletion(secondGen.getId());

        List<GenerationJob> jobs = toolsGenerationService.getJobsForNotebook(
                notebook.getId(),
                user.getId(),
                JobType.FLASHCARDS,
                null,
                "Newest",
                50,
                0
        );
        assertThat(jobs).hasSize(2);

        GenerationJob reloadedFirst = generationJobRepository.findById(firstGen.getId()).orElseThrow();
        GenerationJob reloadedSecond = generationJobRepository.findById(secondGen.getId()).orElseThrow();

        assertThat(reloadedFirst.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(reloadedFirst.getTitle()).isEqualTo("DNA Replication Basics");
        GenerationJobResponse firstResp = toolsGenerationService.toResponse(reloadedFirst);
        assertThat(firstResp.options()).isNotNull();
        assertThat(firstResp.options().difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(firstResp.options().itemCount()).isEqualTo(8);
        assertThat(firstResp.options().sourceCount()).isEqualTo(1);
        assertThat(firstResp.options().selectedSourceIds()).containsExactly(s1.getId());

        assertThat(reloadedSecond.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(reloadedSecond.getTitle()).isEqualTo("Comprehensive Exam Deck");
        GenerationJobResponse secondResp = toolsGenerationService.toResponse(reloadedSecond);
        assertThat(secondResp.options()).isNotNull();
        assertThat(secondResp.options().difficulty()).isEqualTo(Difficulty.HARD);
        assertThat(secondResp.options().itemCount()).isEqualTo(20);
        assertThat(secondResp.options().sourceCount()).isEqualTo(2);

        // Search filter
        List<GenerationJob> searched = toolsGenerationService.getJobsForNotebook(
                notebook.getId(),
                user.getId(),
                JobType.FLASHCARDS,
                "Comprehensive",
                "Newest",
                50,
                0
        );
        assertThat(searched).extracting(GenerationJob::getId).containsExactly(secondGen.getId());

        // Rename first generation
        GenerationJob renamed = toolsGenerationService
                .renameJob(notebook.getId(), firstGen.getId(), user.getId(), "Okazaki Quick Cards")
                .orElseThrow();
        assertThat(renamed.getTitle()).isEqualTo("Okazaki Quick Cards");
        assertThat(renamed.isHasCustomTitle()).isTrue();

        // Delete second generation while keeping first intact
        boolean deleted = toolsGenerationService.deleteJob(notebook.getId(), secondGen.getId(), user.getId());
        assertThat(deleted).isTrue();

        List<GenerationJob> remaining = toolsGenerationService.getJobsForNotebook(
                notebook.getId(),
                user.getId(),
                null,
                null,
                "Newest",
                50,
                0
        );
        assertThat(remaining).extracting(GenerationJob::getId).containsExactly(firstGen.getId());
    }

    private void awaitCompletion(UUID jobId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            GenerationJob current = generationJobRepository.findById(jobId).orElseThrow();
            if (current.getStatus() == JobStatus.COMPLETED || current.getStatus() == JobStatus.FAILED) {
                return;
            }
            Thread.sleep(50);
        }
    }
}
