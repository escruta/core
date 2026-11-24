package com.escruta.core.services;

import com.escruta.core.dtos.tools.GenerationOptions;
import com.escruta.core.dtos.tools.GenerationOptions.DetailLevel;
import com.escruta.core.dtos.tools.GenerationOptions.Difficulty;
import com.escruta.core.dtos.tools.GenerationRequest;
import com.escruta.core.dtos.tools.MindMapResponse;
import com.escruta.core.dtos.tools.QuestionnaireResponse;
import com.escruta.core.dtos.tools.StudyGuideResponse;
import com.escruta.core.entities.GenerationJob;
import com.escruta.core.entities.GenerationJob.JobStatus;
import com.escruta.core.entities.GenerationJob.JobType;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.Source;
import com.escruta.core.entities.User;
import com.escruta.core.entities.enums.SourceStatus;
import com.escruta.core.repositories.GenerationJobRepository;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.SourceChunkRepository;
import com.escruta.core.repositories.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolsGenerationService & GenerationOptions Tests")
class ToolsGenerationServiceTest {
    @Mock
    private GenerationJobRepository jobRepository;
    @Mock
    private NotebookRepository notebookRepository;
    @Mock
    private SourceRepository sourceRepository;
    @Mock
    private SourceChunkRepository chunkRepository;
    @Mock
    private RetrievalService retrievalService;
    @Mock
    private NotebookContextService notebookContextService;
    @Mock
    private LearningMemoryService learningMemoryService;
    @Mock
    private SseNotificationService sseNotificationService;
    @Mock
    private ChatModel chatModel;
    @Mock
    private PlatformTransactionManager transactionManager;

    private ObjectMapper objectMapper;
    private ToolsGenerationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        Executor directExecutor = Runnable::run;
        service = new ToolsGenerationService(
                jobRepository,
                notebookRepository,
                sourceRepository,
                chunkRepository,
                retrievalService,
                notebookContextService,
                learningMemoryService,
                sseNotificationService,
                chatModel,
                objectMapper,
                directExecutor,
                transactionManager
        );
        ReflectionTestUtils.setField(service, "maxConcurrency", 4);
        service.init();
        lenient().when(learningMemoryService.buildToolGenerationContext(any())).thenReturn("");
        lenient().when(transactionManager.getTransaction(any())).thenReturn(null);
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    }

    @Test
    @DisplayName("GenerationOptions.normalizeFor clamps metrics and resolves source subset")
    void normalizeOptions_shouldClampAndValidateMetrics() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID s3 = UUID.randomUUID();

        GenerationOptions rawFlashcards = new GenerationOptions(
                Difficulty.HARD,
                DetailLevel.DETAILED,
                99,
                null,
                null,
                null,
                null,
                "  Focus on dynamic programming  ",
                List.of(s1, s2),
                null
        );

        GenerationOptions normalized = GenerationOptions.normalizeFor(
                JobType.FLASHCARDS,
                rawFlashcards,
                List.of(s1, s2, s3)
        );

        assertThat(normalized.difficulty()).isEqualTo(Difficulty.HARD);
        assertThat(normalized.detailLevel()).isEqualTo(DetailLevel.DETAILED);
        assertThat(normalized.itemCount()).isEqualTo(30);
        assertThat(normalized.focusTopic()).isEqualTo("Focus on dynamic programming");
        assertThat(normalized.selectedSourceIds()).containsExactly(s1, s2);
        assertThat(normalized.sourceCount()).isEqualTo(2);

        GenerationOptions rawMindMap = new GenerationOptions(
                Difficulty.EASY,
                DetailLevel.CONCISE,
                null,
                9,
                1,
                null,
                null,
                null,
                null,
                null
        );
        GenerationOptions normalizedMap = GenerationOptions.normalizeFor(
                JobType.MIND_MAP,
                rawMindMap,
                List.of(s1, s2, s3)
        );
        assertThat(normalizedMap.branchCount()).isEqualTo(3);
        assertThat(normalizedMap.maxDepth()).isEqualTo(4);
        assertThat(normalizedMap.selectedSourceIds()).isNull();
        assertThat(normalizedMap.sourceCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Allows creating multiple generations of the same tool with different metrics")
    void createAndDispatchJob_shouldAllowMultipleGenerationsOfSameTool() {
        UUID notebookId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sourceA = UUID.randomUUID();
        UUID sourceB = UUID.randomUUID();

        User user = new User();
        user.setId(userId);

        Notebook notebook = new Notebook();
        notebook.setId(notebookId);

        when(notebookRepository.existsByIdAndUserId(notebookId, userId)).thenReturn(true);
        when(notebookRepository.getReferenceById(notebookId)).thenReturn(notebook);
        when(sourceRepository.findReadyIdsByNotebookId(notebookId)).thenReturn(List.of(sourceA, sourceB));
        when(jobRepository.countByNotebookIdAndUserIdAndStatusIn(eq(notebookId), eq(userId), any())).thenReturn(1L);
        when(jobRepository.claimPending(any())).thenReturn(0);
        when(jobRepository.save(any(GenerationJob.class))).thenAnswer(invocation -> {
            GenerationJob j = invocation.getArgument(0);
            if (j.getId() == null) {
                j.setId(UUID.randomUUID());
            }
            return j;
        });

        GenerationOptions easyDeckOptions = new GenerationOptions(
                Difficulty.EASY,
                DetailLevel.CONCISE,
                8,
                null,
                null,
                null,
                null,
                "Basic vocabulary",
                List.of(sourceA),
                null
        );
        GenerationOptions hardDeckOptions = new GenerationOptions(
                Difficulty.HARD,
                DetailLevel.DETAILED,
                25,
                null,
                null,
                null,
                null,
                "Exam edge cases",
                List.of(sourceA, sourceB),
                null
        );

        GenerationJob firstJob = service.createAndDispatchJob(
                notebookId,
                user,
                new GenerationRequest(JobType.FLASHCARDS, null, easyDeckOptions)
        );
        GenerationJob secondJob = service.createAndDispatchJob(
                notebookId,
                user,
                new GenerationRequest(JobType.FLASHCARDS, "Exam Mastery Deck", hardDeckOptions)
        );

        assertThat(firstJob.getId()).isNotEqualTo(secondJob.getId());
        assertThat(firstJob.getTitle()).isEqualTo("Flashcards: Basic vocabulary");
        assertThat(firstJob.isHasCustomTitle()).isFalse();
        assertThat(secondJob.getTitle()).isEqualTo("Exam Mastery Deck");
        assertThat(secondJob.isHasCustomTitle()).isTrue();

        GenerationOptions savedFirstOpts = service.deserializeOptions(firstJob.getOptions());
        GenerationOptions savedSecondOpts = service.deserializeOptions(secondJob.getOptions());
        assertThat(savedFirstOpts).isNotNull();
        assertThat(savedFirstOpts.itemCount()).isEqualTo(8);
        assertThat(savedFirstOpts.difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(savedFirstOpts.sourceCount()).isEqualTo(1);

        assertThat(savedSecondOpts).isNotNull();
        assertThat(savedSecondOpts.itemCount()).isEqualTo(25);
        assertThat(savedSecondOpts.difficulty()).isEqualTo(Difficulty.HARD);
        assertThat(savedSecondOpts.sourceCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("StudyGuide generation enforces selected sections and updates smart title")
    void processJob_studyGuideShouldFilterSectionsAndUpdateTitle() {
        UUID jobId = UUID.randomUUID();
        UUID notebookId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();

        Notebook notebook = new Notebook();
        notebook.setId(notebookId);
        User user = new User();
        user.setId(userId);

        GenerationOptions options = new GenerationOptions(
                Difficulty.HARD,
                DetailLevel.DETAILED,
                null,
                null,
                null,
                null,
                List.of("keyConcepts", "reviewQuestions"),
                "Thermodynamic entropy",
                List.of(sourceId),
                1
        );

        GenerationJob job = new GenerationJob(
                notebook,
                user,
                JobType.STUDY_GUIDE,
                "Study Guide",
                false,
                objectMapper.writeValueAsString(options)
        );
        job.setId(jobId);

        Source spanishSource = new Source();
        spanishSource.setId(sourceId);
        spanishSource.setStatus(SourceStatus.READY);
        spanishSource.setContent("La entropía termodinámica mide el desorden molecular de un sistema aislado.");

        when(jobRepository.claimPending(jobId)).thenReturn(1);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.findUserIdById(jobId)).thenReturn(Optional.of(userId));
        when(jobRepository.findNotebookIdById(jobId)).thenReturn(Optional.of(notebookId));
        when(retrievalService.search(eq(notebookId), eq(List.of(sourceId)), any(), eq(45))).thenReturn(List.of());
        when(chunkRepository.findByNotebookIdAndSourceIdInOrderByChunkIndexAsc(
                eq(notebookId),
                eq(List.of(sourceId)),
                any()
        )).thenReturn(List.of());
        when(sourceRepository.findByNotebookIdAndStatusOrderByCreatedAtDesc(notebookId, SourceStatus.READY)).thenReturn(
                List.of(spanishSource));

        StudyGuideResponse aiOutput = new StudyGuideResponse(
                "Entropía Termodinámica Avanzada",
                "Resumen profundo sobre entropía.",
                List.of(new StudyGuideResponse.KeyConcept("Entropía", "Medida del número de microestados")),
                List.of("Should be filtered out because importantDetails was excluded"),
                List.of("Should be filtered out because connections was excluded"),
                List.of("¿Cómo varía la entropía en un proceso irreversible?")
        );

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        when(chatModel.call(promptCaptor.capture())).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(
                objectMapper.writeValueAsString(aiOutput))))));

        service.processJob(jobId);

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getTitle()).isEqualTo("Entropía Termodinámica Avanzada");

        StudyGuideResponse savedResult = objectMapper.readValue(job.getResult(), StudyGuideResponse.class);
        assertThat(savedResult.keyConcepts()).hasSize(1);
        assertThat(savedResult.reviewQuestions()).hasSize(1);
        assertThat(savedResult.importantDetails()).isEmpty();
        assertThat(savedResult.connections()).isEmpty();

        String sentPromptText = promptCaptor.getValue().getContents();
        assertThat(sentPromptText).contains("ADVANCED");
        assertThat(sentPromptText).contains("Thermodynamic entropy");
    }

    @Test
    @DisplayName("Includes notebook learning memory context in generation prompt when available")
    void processJob_shouldIncludeLearningMemoryContextInPrompt() {
        UUID jobId = UUID.randomUUID();
        UUID notebookId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Notebook notebook = new Notebook();
        notebook.setId(notebookId);
        User user = new User();
        user.setId(userId);

        GenerationOptions options = new GenerationOptions(
                Difficulty.MEDIUM,
                DetailLevel.STANDARD,
                5,
                null,
                null,
                null,
                null,
                null,
                null,
                1
        );

        GenerationJob job = new GenerationJob(
                notebook,
                user,
                JobType.FLASHCARDS,
                "Flashcards",
                false,
                objectMapper.writeValueAsString(options)
        );
        job.setId(jobId);

        when(jobRepository.claimPending(jobId)).thenReturn(1);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.findUserIdById(jobId)).thenReturn(Optional.of(userId));
        when(jobRepository.findNotebookIdById(jobId)).thenReturn(Optional.of(notebookId));
        when(notebookContextService.getContext(notebookId, 30)).thenReturn(
                "Backpropagation computes gradients via the chain rule.");
        when(learningMemoryService.buildToolGenerationContext(notebookId)).thenReturn(
                "- LEARNER MEMORY CONTEXT:\n  * Concepts needing reinforcement: Vanishing gradients");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        when(chatModel.call(promptCaptor.capture())).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(
                """
                        {"title":"Gradient Review","flashcards":[{"front":"What causes vanishing gradients?","back":"Repeated small derivatives shrink gradients."}]}
                        """)))));

        service.processJob(jobId);

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(promptCaptor.getValue().getContents())
                .contains("LEARNER MEMORY CONTEXT")
                .contains("Vanishing gradients");
    }

    @Test
    @DisplayName("MindMap generation trims hierarchy depth to configured maxDepth")
    void processJob_mindMapShouldTrimDepth() {
        UUID jobId = UUID.randomUUID();
        UUID notebookId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Notebook notebook = new Notebook();
        notebook.setId(notebookId);
        User user = new User();
        user.setId(userId);

        GenerationOptions options = new GenerationOptions(
                Difficulty.MEDIUM,
                DetailLevel.CONCISE,
                null,
                2,
                4,
                null,
                null,
                null,
                null,
                1
        );

        GenerationJob job = new GenerationJob(
                notebook,
                user,
                JobType.MIND_MAP,
                "Custom Mind Map Name",
                true,
                objectMapper.writeValueAsString(options)
        );
        job.setId(jobId);

        when(jobRepository.claimPending(jobId)).thenReturn(1);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.findUserIdById(jobId)).thenReturn(Optional.of(userId));
        when(jobRepository.findNotebookIdById(jobId)).thenReturn(Optional.of(notebookId));
        when(notebookContextService.getContext(notebookId, 20)).thenReturn("Distributed systems consensus protocols.");

        MindMapResponse deepMap = new MindMapResponse(
                "Distributed Consensus",
                "Protocols enabling fault-tolerant agreement across distributed nodes.",
                List.of(new MindMapResponse.Branch(
                        "Raft", "Understandable leader-based consensus algorithm.", List.of(new MindMapResponse.Branch(
                        "Leader Election",
                        "Randomized election timeouts select a single cluster leader.",
                        List.of(new MindMapResponse.Branch(
                                "Heartbeat Timeout",
                                "Periodic RPCs maintain authority.",
                                List.of()
                        ))
                ))
                ))
        );

        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(
                objectMapper.writeValueAsString(deepMap))))));

        service.processJob(jobId);

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getTitle()).isEqualTo("Custom Mind Map Name");

        MindMapResponse savedMap = objectMapper.readValue(job.getResult(), MindMapResponse.class);
        assertThat(savedMap.description()).isEqualTo(
                "Protocols enabling fault-tolerant agreement across distributed nodes.");
        assertThat(savedMap.branches()).hasSize(1);
        assertThat(savedMap.branches().getFirst().description()).isEqualTo(
                "Understandable leader-based consensus algorithm.");
        assertThat(savedMap.branches().getFirst().children()).hasSize(1);
        assertThat(savedMap.branches().getFirst().children().getFirst().description()).isEqualTo(
                "Randomized election timeouts select a single cluster leader.");
        assertThat(savedMap.branches().getFirst().children().getFirst().children()).isEmpty();
    }

    @Test
    @DisplayName("Questionnaire generation filters disallowed question types and caps itemCount")
    void processJob_questionnaireShouldFilterQuestionTypesAndCapCount() {
        UUID jobId = UUID.randomUUID();
        UUID notebookId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Notebook notebook = new Notebook();
        notebook.setId(notebookId);
        User user = new User();
        user.setId(userId);

        GenerationOptions options = new GenerationOptions(
                Difficulty.HARD,
                DetailLevel.STANDARD,
                3,
                null,
                null,
                List.of("multiple_choice"),
                null,
                null,
                null,
                1
        );

        GenerationJob job = new GenerationJob(
                notebook,
                user,
                JobType.QUESTIONNAIRE,
                "Questionnaire",
                false,
                objectMapper.writeValueAsString(options)
        );
        job.setId(jobId);

        when(jobRepository.claimPending(jobId)).thenReturn(1);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.findUserIdById(jobId)).thenReturn(Optional.of(userId));
        when(jobRepository.findNotebookIdById(jobId)).thenReturn(Optional.of(notebookId));
        when(notebookContextService.getContext(notebookId, 30)).thenReturn("Operating system kernels and scheduling.");

        QuestionnaireResponse aiQuiz = new QuestionnaireResponse(
                "Kernel Scheduling Quiz", List.of(
                new QuestionnaireResponse.Question(
                        "multiple_choice",
                        "Q1?",
                        List.of("A", "B", "C", "D"),
                        0,
                        null,
                        null,
                        "Exp 1"
                ),
                new QuestionnaireResponse.Question("true_false", "Disallowed TF?", null, null, true, null, "Exp TF"),
                new QuestionnaireResponse.Question(
                        "multiple_choice",
                        "Q2?",
                        List.of("A", "B", "C", "D"),
                        1,
                        null,
                        null,
                        "Exp 2"
                )
        )
        );

        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(
                objectMapper.writeValueAsString(aiQuiz))))));

        service.processJob(jobId);

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getTitle()).isEqualTo("Kernel Scheduling Quiz");
        QuestionnaireResponse savedQuiz = objectMapper.readValue(job.getResult(), QuestionnaireResponse.class);
        assertThat(savedQuiz.questions()).hasSize(2);
        assertThat(savedQuiz.questions()).allMatch(q -> "multiple_choice".equals(q.type()));
    }

    @Test
    @DisplayName("Rejects generation when notebook has no ready sources")
    void createAndDispatchJob_shouldThrowWhenNoReadySources() {
        UUID notebookId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        when(notebookRepository.existsByIdAndUserId(notebookId, userId)).thenReturn(true);
        when(sourceRepository.findReadyIdsByNotebookId(notebookId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createAndDispatchJob(
                notebookId,
                user,
                new GenerationRequest(JobType.FLASHCARDS)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No ready sources");

        verify(sourceRepository).findReadyIdsByNotebookId(notebookId);
    }
}
