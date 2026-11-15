package com.escruta.core.services;

import com.escruta.core.dtos.tools.FlashcardsResponse;
import com.escruta.core.dtos.tools.GenerationJobResponse;
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
import com.escruta.core.entities.SourceChunk;
import com.escruta.core.entities.User;
import com.escruta.core.entities.enums.SourceStatus;
import com.escruta.core.repositories.GenerationJobRepository;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.OffsetPageRequest;
import com.escruta.core.repositories.SourceChunkRepository;
import com.escruta.core.repositories.SourceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToolsGenerationService {
    private static final int MAX_ACTIVE_JOBS_PER_NOTEBOOK = 8;
    private static final int MAX_CONTEXT_CHARS = 60_000;

    private final GenerationJobRepository jobRepository;
    private final NotebookRepository notebookRepository;
    private final SourceRepository sourceRepository;
    private final SourceChunkRepository chunkRepository;
    private final RetrievalService retrievalService;
    private final NotebookContextService notebookContextService;
    private final SseNotificationService sseNotificationService;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final Executor taskExecutor;
    private final PlatformTransactionManager transactionManager;

    @Value("${services.jobs.max-concurrency:10}")
    private int maxConcurrency;

    private TransactionTemplate transactionTemplate;
    private Semaphore jobSlots;
    private ChatClient chatClient;

    @PostConstruct
    public void init() {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.jobSlots = new Semaphore(Math.max(1, maxConcurrency));
        this.chatClient = ChatClient.create(chatModel);
    }

    @Transactional
    public GenerationJob createAndDispatchJob(UUID notebookId, User user, GenerationRequest request) {
        if (!notebookRepository.existsByIdAndUserId(notebookId, user.getId())) {
            throw new NoSuchElementException("Notebook not found");
        }

        List<UUID> readySourceIds = sourceRepository.findReadyIdsByNotebookId(notebookId);
        if (readySourceIds.isEmpty()) {
            throw new IllegalArgumentException("No ready sources available in this notebook");
        }

        long activeCount = jobRepository.countByNotebookIdAndUserIdAndStatusIn(
                notebookId,
                user.getId(),
                List.of(JobStatus.PENDING, JobStatus.PROCESSING)
        );
        if (activeCount >= MAX_ACTIVE_JOBS_PER_NOTEBOOK) {
            throw new IllegalStateException("Too many tool generations are currently running in this notebook");
        }

        GenerationOptions normalizedOptions = GenerationOptions.normalizeFor(
                request.type(),
                request.options(),
                readySourceIds
        );

        boolean hasCustomTitle = request.title() != null && !request.title().isBlank();
        String initialTitle = hasCustomTitle ?
                normalizeTitle(request.title(), request.type().defaultTitle()) :
                buildInitialTitle(request.type(), normalizedOptions);

        Notebook notebook = notebookRepository.getReferenceById(notebookId);
        GenerationJob job = new GenerationJob(
                notebook,
                user,
                request.type(),
                initialTitle,
                hasCustomTitle,
                serializeOptions(normalizedOptions)
        );

        GenerationJob savedJob = jobRepository.save(job);
        notebookRepository.touchLastActivity(notebookId);

        UUID userId = user.getId();
        UUID jobId = savedJob.getId();
        runAfterCommit(() -> {
            publishEvent(savedJob, userId, notebookId);
            dispatch(jobId);
        });

        return savedJob;
    }

    @Transactional
    public GenerationJob retryJob(UUID notebookId, UUID jobId, User user, @Nullable GenerationOptions overrideOptions) {
        GenerationJob job = jobRepository
                .findByIdAndNotebookIdAndUserId(jobId, notebookId, user.getId())
                .orElseThrow(() -> new NoSuchElementException("Generation job not found"));

        if (job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.PROCESSING) {
            throw new IllegalStateException("This generation is already in progress");
        }

        List<UUID> readySourceIds = sourceRepository.findReadyIdsByNotebookId(notebookId);
        if (readySourceIds.isEmpty()) {
            throw new IllegalArgumentException("No ready sources available in this notebook");
        }

        GenerationOptions baseOptions = overrideOptions != null ?
                overrideOptions :
                deserializeOptions(job.getOptions());
        GenerationOptions normalizedOptions = GenerationOptions.normalizeFor(
                job.getType(),
                baseOptions,
                readySourceIds
        );

        job.resetForRetry();
        job.setOptions(serializeOptions(normalizedOptions));
        GenerationJob savedJob = jobRepository.save(job);
        notebookRepository.touchLastActivity(notebookId);

        UUID userId = user.getId();
        runAfterCommit(() -> {
            publishEvent(savedJob, userId, notebookId);
            dispatch(savedJob.getId());
        });

        return savedJob;
    }

    @Transactional
    public Optional<GenerationJob> renameJob(UUID notebookId, UUID jobId, UUID userId, String title) {
        return jobRepository.findByIdAndNotebookIdAndUserId(jobId, notebookId, userId).map(job -> {
            job.setTitle(normalizeTitle(title, job.getType().defaultTitle()));
            job.setHasCustomTitle(true);
            GenerationJob saved = jobRepository.save(job);
            notebookRepository.touchLastActivity(notebookId);
            runAfterCommit(() -> publishEvent(saved, userId, notebookId));
            return saved;
        });
    }

    @Transactional
    public boolean deleteJob(UUID notebookId, UUID jobId, UUID userId) {
        GenerationJob job = jobRepository.findByIdAndNotebookIdAndUserId(jobId, notebookId, userId).orElse(null);
        if (job == null) {
            return false;
        }
        JobType type = job.getType();
        jobRepository.delete(job);
        notebookRepository.touchLastActivity(notebookId);
        runAfterCommit(() -> publishDeletedEvent(jobId, type, userId, notebookId));
        return true;
    }

    public Optional<GenerationJob> getJob(UUID notebookId, UUID jobId, UUID userId) {
        return jobRepository.findByIdAndNotebookIdAndUserId(jobId, notebookId, userId);
    }

    public List<GenerationJob> getJobsForNotebook(
            UUID notebookId,
            UUID userId,
            @Nullable JobType type,
            @Nullable String search,
            String sort,
            int limit,
            int offset
    ) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        long boundedOffset = Math.max(0, offset);
        OffsetPageRequest pageable = new OffsetPageRequest(boundedOffset, boundedLimit, buildSort(sort));
        String term = search != null && !search.isBlank() ?
                search.trim() :
                null;

        if (type != null && term != null) {
            return jobRepository.findByNotebookIdAndUserIdAndTypeAndTitleContainingIgnoreCase(
                    notebookId,
                    userId,
                    type,
                    term,
                    pageable
            );
        }
        if (type != null) {
            return jobRepository.findByNotebookIdAndUserIdAndType(notebookId, userId, type, pageable);
        }
        if (term != null) {
            return jobRepository.findByNotebookIdAndUserIdAndTitleContainingIgnoreCase(
                    notebookId,
                    userId,
                    term,
                    pageable
            );
        }
        return jobRepository.findByNotebookIdAndUserId(notebookId, userId, pageable);
    }

    public Optional<GenerationJob> getLatestCompletedJob(UUID notebookId, UUID userId, JobType type) {
        return jobRepository.findLatestCompletedByType(notebookId, userId, type);
    }

    public List<GenerationJob> getActiveJobs(UUID notebookId, UUID userId, JobType type) {
        return jobRepository.findActiveJobsByType(
                notebookId,
                userId,
                type,
                List.of(JobStatus.PENDING, JobStatus.PROCESSING)
        );
    }

    public GenerationJobResponse toResponse(GenerationJob job) {
        return GenerationJobResponse.from(job, deserializeOptions(job.getOptions()));
    }

    public @Nullable GenerationOptions deserializeOptions(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, GenerationOptions.class);
        } catch (Exception e) {
            log.warn("Could not deserialize generation options: {}", e.getMessage());
            return null;
        }
    }

    private @Nullable String serializeOptions(@Nullable GenerationOptions options) {
        if (options == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            log.warn("Could not serialize generation options: {}", e.getMessage());
            return null;
        }
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    public void dispatch(UUID jobId) {
        taskExecutor.execute(() -> processJob(jobId));
    }

    public void processJob(UUID jobId) {
        try {
            jobSlots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        try {
            if (jobRepository.claimPending(jobId) == 0) {
                return;
            }

            GenerationJob job = jobRepository.findById(jobId).orElse(null);
            if (job == null) {
                return;
            }

            UUID userId = jobRepository.findUserIdById(jobId).orElse(null);
            UUID notebookId = jobRepository.findNotebookIdById(jobId).orElse(null);
            if (userId == null || notebookId == null) {
                return;
            }

            publishEvent(job, userId, notebookId);

            try {
                GeneratedArtifact artifact = generateContent(job, notebookId);
                transactionTemplate.executeWithoutResult(_ -> {
                    if (!job.isHasCustomTitle() && artifact.suggestedTitle() != null && !artifact
                            .suggestedTitle()
                            .isBlank()) {
                        job.setTitle(normalizeTitle(artifact.suggestedTitle(), job.getTitle()));
                    }
                    job.markAsCompleted(artifact.jsonResult());
                    jobRepository.save(job);
                });
            } catch (Exception e) {
                log.error("Tool generation job {} ({}) failed: {}", jobId, job.getType(), e.getMessage(), e);
                transactionTemplate.executeWithoutResult(_ -> {
                    job.markAsFailed(e.getMessage() != null ?
                            e.getMessage() :
                            "Generation failed");
                    jobRepository.save(job);
                });
            }

            notebookRepository.touchLastActivity(notebookId);
            publishEvent(job, userId, notebookId);
        } finally {
            jobSlots.release();
        }
    }

    private void publishEvent(GenerationJob job, UUID userId, UUID notebookId) {
        if (userId == null) {
            return;
        }
        sseNotificationService.publish(userId, "tool.updated", toolEventPayload(job, notebookId));
    }

    private void publishDeletedEvent(UUID jobId, JobType type, UUID userId, UUID notebookId) {
        if (userId == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notebookId", notebookId.toString());
        payload.put("jobId", jobId.toString());
        payload.put("type", type.toString());
        sseNotificationService.publish(userId, "tool.deleted", payload);
    }

    private Map<String, Object> toolEventPayload(GenerationJob job, UUID notebookId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notebookId", notebookId.toString());
        payload.put("jobId", job.getId().toString());
        payload.put("type", job.getType().toString());
        payload.put("title", job.getTitle());
        payload.put("status", job.getStatus().toString());
        payload.put("options", deserializeOptions(job.getOptions()));
        payload.put(
                "result",
                job.getResult() != null ?
                        job.getResult() :
                        ""
        );
        payload.put(
                "errorMessage",
                job.getErrorMessage() != null ?
                        job.getErrorMessage() :
                        ""
        );
        payload.put(
                "createdAt",
                job.getCreatedAt() != null ?
                        job.getCreatedAt().toString() :
                        null
        );
        payload.put(
                "updatedAt",
                job.getUpdatedAt() != null ?
                        job.getUpdatedAt().toString() :
                        null
        );
        payload.put(
                "completedAt",
                job.getCompletedAt() != null ?
                        job.getCompletedAt().toString() :
                        null
        );
        return payload;
    }

    private GeneratedArtifact generateContent(GenerationJob job, UUID notebookId) {
        GenerationOptions options = deserializeOptions(job.getOptions());
        if (options == null) {
            List<UUID> readySourceIds = sourceRepository.findReadyIdsByNotebookId(notebookId);
            options = GenerationOptions.normalizeFor(job.getType(), null, readySourceIds);
        }

        String context = resolveContext(notebookId, options);
        if (context.isBlank()) {
            throw new IllegalStateException("No sources available or content not yet indexed");
        }

        return switch (job.getType()) {
            case STUDY_GUIDE -> generateStudyGuide(context, options);
            case FLASHCARDS -> generateFlashcards(context, options);
            case QUESTIONNAIRE -> generateQuestionnaire(context, options);
            case MIND_MAP -> generateMindMap(context, options);
        };
    }

    private String resolveContext(UUID notebookId, GenerationOptions options) {
        DetailLevel detail = options.detailLevel() != null ?
                options.detailLevel() :
                DetailLevel.STANDARD;
        int chunkLimit = switch (detail) {
            case CONCISE -> 20;
            case STANDARD -> 30;
            case DETAILED -> 45;
        };

        List<UUID> selectedSourceIds = options.selectedSourceIds();
        boolean hasSelectedSubset = selectedSourceIds != null && !selectedSourceIds.isEmpty();
        boolean hasFocusTopic = options.focusTopic() != null && !options.focusTopic().isBlank();

        if (!hasSelectedSubset && !hasFocusTopic) {
            String cachedContext = notebookContextService.getContext(notebookId, chunkLimit);
            if (!cachedContext.isBlank()) {
                return cachedContext;
            }
        }

        String searchQuery = hasFocusTopic ?
                options.focusTopic() + " " + NotebookContextService.DEFAULT_QUERY :
                NotebookContextService.DEFAULT_QUERY;

        List<Document> searchDocs = retrievalService.search(
                notebookId,
                hasSelectedSubset ?
                        selectedSourceIds :
                        null,
                searchQuery,
                chunkLimit
        );

        String context = searchDocs
                .stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n\n"));

        if (context.isBlank()) {
            List<SourceChunk> orderedChunks = hasSelectedSubset ?
                    chunkRepository.findByNotebookIdAndSourceIdInOrderByChunkIndexAsc(
                            notebookId,
                            selectedSourceIds,
                            PageRequest.of(0, chunkLimit)
                    ) :
                    chunkRepository.findByNotebookIdOrderByChunkIndexAsc(notebookId, PageRequest.of(0, chunkLimit));

            context = orderedChunks
                    .stream()
                    .map(SourceChunk::getContent)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining("\n\n"));
        }

        if (context.isBlank()) {
            Set<UUID> allowed = hasSelectedSubset ?
                    new HashSet<>(selectedSourceIds) :
                    Set.of();
            List<Source> readySources = sourceRepository.findByNotebookIdAndStatusOrderByCreatedAtDesc(
                    notebookId,
                    SourceStatus.READY
            );
            context = readySources
                    .stream()
                    .filter(s -> allowed.isEmpty() || allowed.contains(s.getId()))
                    .map(Source::getContent)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining("\n\n"));
        }

        return context.length() > MAX_CONTEXT_CHARS ?
                context.substring(0, MAX_CONTEXT_CHARS) :
                context;
    }

    private GeneratedArtifact generateStudyGuide(String context, GenerationOptions options) {
        Difficulty difficulty = options.difficulty() != null ?
                options.difficulty() :
                Difficulty.MEDIUM;
        DetailLevel detail = options.detailLevel() != null ?
                options.detailLevel() :
                DetailLevel.STANDARD;
        Set<String> activeSections = new HashSet<>(options.sections() != null && !options.sections().isEmpty() ?
                options.sections() :
                GenerationOptions.VALID_STUDY_GUIDE_SECTIONS);

        String audienceDirective = switch (difficulty) {
            case EASY ->
                    "Target an INTRODUCTORY audience: use clear, accessible language, relatable analogies, and explain technical terms gently.";
            case MEDIUM ->
                    "Target an INTERMEDIATE audience: balance clarity with accurate domain terminology and conceptual depth.";
            case HARD ->
                    "Target an ADVANCED audience: use rigorous terminology, highlight subtle nuances, trade-offs, and critical analysis.";
        };

        String densityDirective = switch (detail) {
            case CONCISE ->
                    "Keep the guide CONCISE: write a tight 2-3 sentence overview, 4-6 key concepts, 4-5 important details, 3 connections, and 3 review questions.";
            case STANDARD ->
                    "Provide a BALANCED guide: write a clear 3-4 sentence overview, 8-12 key concepts, 6-8 important details, 4-6 connections, and 5-7 review questions.";
            case DETAILED ->
                    "Provide an IN-DEPTH, exhaustive guide: write a thorough 4-6 sentence overview, 12-18 key concepts with rich definitions, 10-14 important details with concrete examples, 6-8 deep connections, and 8-10 challenging review questions.";
        };

        String sectionsDirective = buildStudyGuideSectionsDirective(activeSections);
        String focusDirective = buildFocusDirective(options.focusTopic());

        String systemPrompt = """
                You are an expert educator creating a structured study guide strictly grounded in the provided materials.
                Write in the same language as the provided materials unless the focus instructions request a specific language.
                
                STRUCTURE:
                - title: A concise, descriptive title (3-8 words) capturing the central subject of this study guide
                - overview: A clear introduction to the central theme
                %s
                
                METRICS & CUSTOMIZATION:
                - %s
                - %s
                %s
                """.formatted(sectionsDirective, audienceDirective, densityDirective, focusDirective);

        StudyGuideResponse response = chatClient
                .prompt()
                .system(systemPrompt)
                .user("Create a customized study guide from these materials:\n\n" + context)
                .call()
                .entity(StudyGuideResponse.class);

        if (response == null) {
            throw new IllegalStateException("AI provider returned an empty study guide");
        }

        StudyGuideResponse sanitized = new StudyGuideResponse(
                response.title(),
                response.overview() != null ?
                        response.overview() :
                        "",
                activeSections.contains("keyConcepts") && response.keyConcepts() != null ?
                        response.keyConcepts() :
                        List.of(),
                activeSections.contains("importantDetails") && response.importantDetails() != null ?
                        response.importantDetails() :
                        List.of(),
                activeSections.contains("connections") && response.connections() != null ?
                        response.connections() :
                        List.of(),
                activeSections.contains("reviewQuestions") && response.reviewQuestions() != null ?
                        response.reviewQuestions() :
                        List.of()
        );

        return new GeneratedArtifact(objectMapper.writeValueAsString(sanitized), sanitized.title());
    }

    private static String buildStudyGuideSectionsDirective(Set<String> activeSections) {
        StringBuilder sb = new StringBuilder();
        if (activeSections.contains("keyConcepts")) {
            sb.append("- keyConcepts: Essential terms and their definitions\n");
        } else {
            sb.append("- keyConcepts: Return an empty list []\n");
        }
        if (activeSections.contains("importantDetails")) {
            sb.append("- importantDetails: Crucial supporting facts, evidence, and examples\n");
        } else {
            sb.append("- importantDetails: Return an empty list []\n");
        }
        if (activeSections.contains("connections")) {
            sb.append("- connections: How different concepts relate to form the unified theme\n");
        } else {
            sb.append("- connections: Return an empty list []\n");
        }
        if (activeSections.contains("reviewQuestions")) {
            sb.append("- reviewQuestions: Open-ended questions testing understanding of the material");
        } else {
            sb.append("- reviewQuestions: Return an empty list []");
        }
        return sb.toString();
    }

    private GeneratedArtifact generateFlashcards(String context, GenerationOptions options) {
        int cardCount = options.itemCount() != null ?
                Math.clamp(options.itemCount(), 5, 30) :
                15;
        Difficulty difficulty = options.difficulty() != null ?
                options.difficulty() :
                Difficulty.MEDIUM;
        DetailLevel detail = options.detailLevel() != null ?
                options.detailLevel() :
                DetailLevel.STANDARD;

        String difficultyDirective = switch (difficulty) {
            case EASY -> "Difficulty: EASY — Focus on foundational definitions, primary vocabulary, and direct recall.";
            case MEDIUM ->
                    "Difficulty: MEDIUM — Focus on conceptual understanding, relationships, and cause-and-effect.";
            case HARD ->
                    "Difficulty: HARD — Focus on subtle distinctions, multi-step application, synthesis, and non-obvious insights.";
        };

        String detailDirective = switch (detail) {
            case CONCISE ->
                    "Answer style: CONCISE — Keep each 'back' crisp and punchy (1 short sentence, ~15-25 words) for rapid spaced repetition.";
            case STANDARD -> "Answer style: STANDARD — Keep each 'back' clear and self-contained (1-2 sentences).";
            case DETAILED ->
                    "Answer style: DETAILED — Provide a thorough answer (2-4 sentences) including context or a concrete example.";
        };

        String focusDirective = buildFocusDirective(options.focusTopic());

        String systemPrompt = """
                You are an expert educator designing flashcards strictly grounded in the provided materials.
                Write in the same language as the provided materials unless the focus instructions request a specific language.
                
                STRUCTURE:
                - title: A concise, descriptive deck title (3-7 words) reflecting the topic
                - flashcards: Create EXACTLY %d high-quality flashcards, each with:
                  - front: A clear question, prompt, or key term
                  - back: An accurate answer or explanation
                
                METRICS & CUSTOMIZATION:
                - Card count: Exactly %d cards
                - %s
                - %s
                %s
                """.formatted(cardCount, cardCount, difficultyDirective, detailDirective, focusDirective);

        FlashcardsResponse response = chatClient
                .prompt()
                .system(systemPrompt)
                .user("Create " + cardCount + " customized flashcards from these materials:\n\n" + context)
                .call()
                .entity(FlashcardsResponse.class);

        if (response == null || response.flashcards() == null) {
            throw new IllegalStateException("AI provider returned empty flashcards");
        }

        List<FlashcardsResponse.Flashcard> cards = response.flashcards().size() > cardCount ?
                response.flashcards().subList(0, cardCount) :
                response.flashcards();
        FlashcardsResponse sanitized = new FlashcardsResponse(response.title(), cards);

        return new GeneratedArtifact(objectMapper.writeValueAsString(sanitized), sanitized.title());
    }

    private GeneratedArtifact generateQuestionnaire(String context, GenerationOptions options) {
        int questionCount = options.itemCount() != null ?
                Math.clamp(options.itemCount(), 3, 25) :
                10;
        Difficulty difficulty = options.difficulty() != null ?
                options.difficulty() :
                Difficulty.MEDIUM;
        DetailLevel detail = options.detailLevel() != null ?
                options.detailLevel() :
                DetailLevel.STANDARD;
        List<String> allowedTypes = options.questionTypes() != null && !options.questionTypes().isEmpty() ?
                options.questionTypes() :
                GenerationOptions.VALID_QUESTION_TYPES;

        String difficultyDirective = switch (difficulty) {
            case EASY -> "Difficulty: EASY — Test foundational comprehension, core facts, and main definitions.";
            case MEDIUM ->
                    "Difficulty: MEDIUM — Test conceptual relationships, reasoning, and interpretation of the materials.";
            case HARD ->
                    "Difficulty: HARD — Test deep critical thinking, synthesis across concepts, edge cases, and plausible distractors.";
        };

        String explanationDirective = switch (detail) {
            case CONCISE -> "Explanation depth: CONCISE — Provide a brief 1-sentence explanation for each answer.";
            case STANDARD ->
                    "Explanation depth: STANDARD — Provide a clear 2-sentence explanation connecting the answer to the core concepts.";
            case DETAILED ->
                    "Explanation depth: DETAILED — Provide an in-depth explanation covering why the correct answer holds and why common misconceptions fail.";
        };

        String allowedTypesJoined = String.join(", ", allowedTypes);
        String focusDirective = buildFocusDirective(options.focusTopic());

        String systemPrompt = """
                You are an expert educator creating an interactive questionnaire strictly grounded in the provided materials.
                Write in the same language as the provided materials unless the focus instructions request a specific language.
                
                STRUCTURE:
                - title: A concise, descriptive title (3-8 words) reflecting the questionnaire subject
                - questions: Create EXACTLY %d questions using ONLY the allowed type(s): [%s]
                  Each question has:
                  - type: MUST be one of [%s]
                  - question: The question text
                  - options: List of 4 options (ONLY when type is "multiple_choice", use null otherwise)
                  - correctAnswerIndex: 0-based index of the correct option (ONLY when type is "multiple_choice", use null otherwise)
                  - correctAnswerBoolean: true or false (ONLY when type is "true_false", use null otherwise)
                  - sampleAnswer: Expected model answer (ONLY when type is "short_answer", use null otherwise)
                  - explanation: Rationale for the correct answer
                
                METRICS & CUSTOMIZATION:
                - Question count: Exactly %d questions
                - Allowed question types: ONLY [%s] (distribute evenly if multiple types are allowed; NEVER output a disallowed type)
                - %s
                - %s
                %s
                """.formatted(
                questionCount,
                allowedTypesJoined,
                allowedTypesJoined,
                questionCount,
                allowedTypesJoined,
                difficultyDirective,
                explanationDirective,
                focusDirective
        );

        QuestionnaireResponse response = chatClient
                .prompt()
                .system(systemPrompt)
                .user("Create a " + questionCount + "-question customized questionnaire from these materials:\n\n" + context)
                .call()
                .entity(QuestionnaireResponse.class);

        if (response == null || response.questions() == null) {
            throw new IllegalStateException("AI provider returned an empty questionnaire");
        }

        Set<String> allowedSet = new HashSet<>(allowedTypes);
        List<QuestionnaireResponse.Question> filtered = response
                .questions()
                .stream()
                .filter(q -> q != null && q.type() != null && allowedSet.contains(q.type()))
                .limit(questionCount)
                .toList();

        List<QuestionnaireResponse.Question> finalQuestions = filtered.isEmpty() ?
                response.questions().stream().limit(questionCount).toList() :
                filtered;

        QuestionnaireResponse sanitized = new QuestionnaireResponse(response.title(), finalQuestions);
        return new GeneratedArtifact(objectMapper.writeValueAsString(sanitized), sanitized.title());
    }

    private GeneratedArtifact generateMindMap(String context, GenerationOptions options) {
        Difficulty difficulty = options.difficulty() != null ?
                options.difficulty() :
                Difficulty.MEDIUM;
        DetailLevel detail = options.detailLevel() != null ?
                options.detailLevel() :
                DetailLevel.STANDARD;
        int branchCount = options.branchCount() != null ?
                Math.clamp(options.branchCount(), 3, 10) :
                6;
        int maxDepth = options.maxDepth() != null ?
                Math.clamp(options.maxDepth(), 2, 4) :
                3;

        String difficultyDirective = switch (difficulty) {
            case EASY ->
                    "Conceptual level: INTRODUCTORY — Organize around high-level, approachable themes and clear labels.";
            case MEDIUM ->
                    "Conceptual level: INTERMEDIATE — Organize around core domain concepts and their structural relationships.";
            case HARD ->
                    "Conceptual level: ADVANCED — Organize around mechanisms, architectural/theoretical trade-offs, and technical sub-components.";
        };

        String detailDirective = switch (detail) {
            case CONCISE ->
                    "Node style: COMPACT — Keep branch labels crisp (1-3 words), write a brief 1-sentence description per node, and include 2-3 sub-branches per node.";
            case STANDARD ->
                    "Node style: BALANCED — Use clear branch labels (2-5 words), write a concise 1-2 sentence description per node, and include 2-4 sub-branches per node.";
            case DETAILED ->
                    "Node style: EXPANDED — Use descriptive labels (2-6 words), write an informative 2-sentence description per node with key context, and include 3-5 sub-branches per node.";
        };

        String focusDirective = buildFocusDirective(options.focusTopic());

        String systemPrompt = """
                You are an expert at synthesizing knowledge into hierarchical mind maps strictly grounded in the provided materials.
                Write in the same language as the provided materials unless the focus instructions request a specific language.
                
                STRUCTURE:
                - central: The central concept or theme (concise 2-6 words)
                - description: A brief, clear explanation (1-2 sentences) summarizing the central concept based on the materials
                - branches: Create %d main branches, each with:
                  - label: Branch label
                  - description: A brief, clear explanation (1-2 sentences) of this specific node and how it relates to its parent concept
                  - children: Nested sub-branches up to %d hierarchy levels deep from the central topic (every child node must also include its own label, description, and children; use empty list [] at leaf nodes)
                
                METRICS & CUSTOMIZATION:
                - Main branches: Approximately %d primary branches
                - Maximum hierarchy depth: %d levels (Level 1 = main branches, Level %d = deepest leaf nodes)
                - %s
                - %s
                %s
                """.formatted(
                branchCount,
                maxDepth,
                branchCount,
                maxDepth,
                maxDepth,
                difficultyDirective,
                detailDirective,
                focusDirective
        );

        MindMapResponse response = chatClient
                .prompt()
                .system(systemPrompt)
                .user("Create a hierarchical mind map with brief node explanations from these materials:\n\n" + context)
                .call()
                .entity(MindMapResponse.class);

        if (response == null || response.branches() == null) {
            throw new IllegalStateException("AI provider returned an empty mind map");
        }

        List<MindMapResponse.Branch> trimmedBranches = response
                .branches()
                .stream()
                .limit(branchCount + 2L)
                .map(branch -> trimBranchDepth(branch, 1, maxDepth))
                .toList();

        MindMapResponse sanitized = new MindMapResponse(response.central(), response.description(), trimmedBranches);
        return new GeneratedArtifact(objectMapper.writeValueAsString(sanitized), sanitized.central());
    }

    private static MindMapResponse.Branch trimBranchDepth(
            MindMapResponse.Branch branch,
            int currentDepth,
            int maxDepth
    ) {
        if (branch.children() == null || branch.children().isEmpty() || currentDepth >= maxDepth) {
            return new MindMapResponse.Branch(branch.label(), branch.description(), List.of());
        }
        List<MindMapResponse.Branch> trimmedChildren = branch
                .children()
                .stream()
                .map(child -> trimBranchDepth(child, currentDepth + 1, maxDepth))
                .toList();
        return new MindMapResponse.Branch(branch.label(), branch.description(), trimmedChildren);
    }

    private static String buildFocusDirective(@Nullable String focusTopic) {
        if (focusTopic == null || focusTopic.isBlank()) {
            return "";
        }
        return "- SPECIFIC FOCUS / INSTRUCTIONS (prioritize this angle while staying grounded in the materials): " + focusTopic.trim();
    }

    private static String buildInitialTitle(JobType type, GenerationOptions options) {
        String base = type.defaultTitle();
        if (options.focusTopic() != null && !options.focusTopic().isBlank()) {
            String focus = options.focusTopic().trim().replaceAll("\\s+", " ");
            if (focus.length() > 42) {
                focus = focus.substring(0, 39).trim() + "...";
            }
            return base + ": " + focus;
        }
        return base;
    }

    private static String normalizeTitle(@Nullable String title, String fallback) {
        if (title == null || title.isBlank()) {
            return fallback;
        }
        String cleaned = title.replaceAll("^[\"']|[\"']$", "").trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return fallback;
        }
        if (cleaned.length() > 200) {
            return cleaned.substring(0, 197) + "...";
        }
        return cleaned;
    }

    private static Sort buildSort(@Nullable String sort) {
        if (sort == null) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return getOrders(sort);
    }

    @NonNull
    static Sort getOrders(@NotNull String sort) {
        return switch (sort) {
            case "Oldest" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "Alphabetical" -> Sort.by(Sort.Direction.ASC, "title");
            case "Reverse Alphabetical" -> Sort.by(Sort.Direction.DESC, "title");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    private record GeneratedArtifact(
            String jsonResult,
            @Nullable
            String suggestedTitle
    ) {
    }
}
