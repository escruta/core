package com.escruta.core.services;

import com.escruta.core.dtos.ExtractorResponse;
import com.escruta.core.dtos.NotebookSummaryDTO;
import com.escruta.core.dtos.SummaryResponse;
import com.escruta.core.entities.Source;
import com.escruta.core.entities.SourceJob;
import com.escruta.core.entities.enums.SourceStatus;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.SourceJobRepository;
import com.escruta.core.repositories.SourceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class SourceJobService {
    private final SourceJobRepository jobRepository;
    private final SourceRepository sourceRepository;
    private final NotebookRepository notebookRepository;
    private final HelperService helperService;
    private final AsyncChunkIndexingService asyncChunkIndexingService;
    private final NotebookContextService notebookContextService;
    private final ChatModel chatModel;
    private final SseNotificationService sseNotificationService;
    private final Executor taskExecutor;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    @Value("${services.jobs.max-concurrency:10}")
    private int maxConcurrency;

    @Value("${services.jobs.summary-parallelism:5}")
    private int summaryParallelism;

    @Value("${services.jobs.summary-max-chunks:12}")
    private int summaryMaxChunks;

    private TransactionTemplate transactionTemplate;
    private Semaphore jobSlots;
    private ChatClient chatClient;

    @PostConstruct
    public void init() {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.jobSlots = new Semaphore(Math.max(1, maxConcurrency));
        this.chatClient = ChatClient.create(chatModel);
    }

    private static final int SUMMARY_CHUNK_SIZE = 50000;
    private static final long STALE_JOB_MINUTES = 30;

    private static final String SOURCE_SUMMARY_SYSTEM_PROMPT = """
            You are an expert at distilling complex information into clear, concise summaries.
            Your task is to create a summary of the provided content that captures its essential information and main points.
            
            RULES:
            - Write a concise paragraph of 2-3 sentences.
            - Focus on the **key concepts**, **findings**, or **conclusions**.
            - Use **bold** for key terms and *italic* for emphasis (sparingly).
            - Start directly with the subject matter.
            - Do NOT use introductory phrases like "This content discusses..." or "The author says...".
            """;

    private static final String NOTEBOOK_SUMMARY_SYSTEM_PROMPT = """
            You are an expert at identifying the core essence and central themes of a set of study materials.
            Your task is to write a comprehensive summary paragraph of 4-6 lines about the central subject matter of this notebook.
            
            RULES:
            - Focus on the **CENTRAL THEME** and **UNIFYING CONCEPTS** of all provided sources
            - Identify the main subject and its most significant aspects
            - Use **bold** for key terms and *italic* for emphasis (sparingly)
            - Write as if explaining the topic directly to a student, providing a clear bird's-eye view
            - Do NOT start with phrases like "The articles...", "The sources...", "This content..." or similar
            - Start directly with the core subject matter (e.g., "Quantum computing is a field that...")
            - Ensure the summary provides a cohesive understanding of how the various pieces of information relate to each other
            """;

    private static final String NOTEBOOK_SUMMARY_TOPICS_SYSTEM_PROMPT = NOTEBOOK_SUMMARY_SYSTEM_PROMPT + """
            
            - Additionally, extract a list of 5 to 8 "topics": short labels (1-3 words) naming the most important concepts, themes or entities covered
            - Each topic must be a concise noun phrase, not a full sentence
            - Do NOT repeat the summary text as a topic, and do NOT use generic words like "introduction" or "overview"
            - Order topics from most to least important
            """;

    @Transactional
    public void startExtractJob(Source source, String filePath, String fileName) {
        SourceJob job = new SourceJob(source, source.getNotebook(), SourceJob.JobType.EXTRACT);
        job.setFilePath(filePath);
        job.setFileName(fileName);
        job = jobRepository.save(job);
        dispatchAfterCommit(job.getId());
    }

    @Transactional
    public SourceJob startSourceSummaryJob(UUID sourceId, UUID notebookId) {
        SourceJob existing = jobRepository.findFirstBySourceIdAndTypeAndStatusInOrderByCreatedAtDesc(
                sourceId,
                SourceJob.JobType.SOURCE_SUMMARY,
                List.of(SourceJob.JobStatus.PENDING, SourceJob.JobStatus.PROCESSING)
        ).orElse(null);
        if (existing != null) {
            return existing;
        }
        SourceJob job = jobRepository.save(new SourceJob(
                sourceRepository.getReferenceById(sourceId),
                notebookRepository.getReferenceById(notebookId),
                SourceJob.JobType.SOURCE_SUMMARY
        ));
        dispatchAfterCommit(job.getId());
        return job;
    }

    @Transactional
    public SourceJob startNotebookSummaryJob(UUID notebookId) {
        SourceJob existing = jobRepository.findFirstByNotebookIdAndTypeAndStatusInOrderByCreatedAtDesc(
                notebookId,
                SourceJob.JobType.NOTEBOOK_SUMMARY,
                List.of(SourceJob.JobStatus.PENDING, SourceJob.JobStatus.PROCESSING)
        ).orElse(null);
        if (existing != null) {
            return existing;
        }
        SourceJob job = jobRepository.save(new SourceJob(
                null,
                notebookRepository.getReferenceById(notebookId),
                SourceJob.JobType.NOTEBOOK_SUMMARY
        ));
        dispatchAfterCommit(job.getId());
        return job;
    }

    public void dispatchAfterCommit(UUID jobId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch(jobId);
            }
        });
    }

    public void dispatch(UUID jobId) {
        taskExecutor.execute(() -> {
            try {
                jobSlots.acquire();
                try {
                    processJob(jobId);
                } finally {
                    jobSlots.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public void processJob(UUID jobId) {
        Instant staleBefore = Instant.now().minus(STALE_JOB_MINUTES, ChronoUnit.MINUTES);
        if (jobRepository.claim(jobId, staleBefore) == 0) {
            return;
        }

        SourceJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }

        try {
            UUID ownerId = switch (job.getType()) {
                case EXTRACT -> processExtract(job);
                case SOURCE_SUMMARY -> processSourceSummary(job);
                case NOTEBOOK_SUMMARY -> processNotebookSummary(job);
            };

            job.markAsCompleted(job.getResult());
            jobRepository.save(job);
            publishCompletion(job, ownerId);
        } catch (Exception e) {
            log.error("Source job {} ({}) failed: {}", jobId, job.getType(), e.getMessage(), e);
            try {
                if (job.getType() == SourceJob.JobType.EXTRACT) {
                    recordExtractFailure(job, e.getMessage());
                } else {
                    job.markAsFailed(e.getMessage());
                    jobRepository.save(job);
                    notebookRepository.touchLastActivity(job.getNotebook().getId());
                }
                publishFailure(job);
            } catch (Exception recordError) {
                log.warn("Could not record failure for source job {}: {}", jobId, recordError.getMessage());
            }
        } finally {
            deleteTempFile(job);
        }
    }

    private void recordExtractFailure(SourceJob job, String errorMessage) {
        transactionTemplate.executeWithoutResult(_ -> {
            job.markAsFailed(errorMessage);
            jobRepository.save(job);
            sourceRepository.updateStatus(job.getSource().getId(), SourceStatus.FAILED);
        });
        notebookRepository.touchLastActivity(job.getNotebook().getId());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumePendingJobs() {
        // No job can be running in this instance before ApplicationReadyEvent.
        // Any PROCESSING job is orphaned, even if it is not old enough for the
        // stale check used during normal claiming.
        int requeued = jobRepository.requeueProcessingJobs();
        if (requeued > 0) {
            log.info("Requeued {} orphaned source jobs", requeued);
        }

        List<SourceJob> pending = jobRepository.findByStatusIn(List.of(
                SourceJob.JobStatus.PENDING,
                SourceJob.JobStatus.PROCESSING
        ));
        if (pending.isEmpty()) {
            return;
        }
        log.info("Resuming {} pending source jobs", pending.size());
        pending.forEach(job -> dispatch(job.getId()));
    }

    private UUID processExtract(SourceJob job) {
        Source source = sourceRepository
                .findById(job.getSource().getId())
                .orElseThrow(() -> new IllegalStateException("Source no longer exists"));
        UUID notebookId = source.getNotebook().getId();

        String title;
        String content;
        if (job.getFilePath() != null) {
            Path file = Path.of(job.getFilePath());
            if (!Files.exists(file)) {
                throw new IllegalStateException("Uploaded file no longer exists");
            }
            ExtractorResponse response = helperService.extractMarkdown(file.toFile(), job.getFileName());
            content = response.content();
            title = null;
        } else if (source.getLink() != null && !source.getLink().isBlank()) {
            ExtractorResponse response = helperService.extractMarkdown(source.getLink());
            title = response.title();
            content = response.content();
        } else {
            title = source.getTitle();
            content = source.getContent();
        }

        if (content == null || content.isBlank()) {
            throw new IllegalStateException("No readable content could be extracted from the source");
        }

        asyncChunkIndexingService.indexSource(
                notebookId,
                source.getId(),
                title != null ?
                        title :
                        source.getTitle(),
                source.getLink(),
                content
        );

        source.setTitle(title != null ?
                title :
                source.getTitle());
        source.setContent(content);
        source.setStatus(SourceStatus.READY);
        sourceRepository.save(source);
        notebookRepository.touchLastActivity(notebookId);
        notebookContextService.evict();

        return sourceRepository.findNotebookOwnerId(source.getId());
    }

    private UUID processSourceSummary(SourceJob job) {
        Source source = sourceRepository
                .findById(job.getSource().getId())
                .orElseThrow(() -> new IllegalStateException("Source no longer exists"));

        String summary = summarizeInChunks(source.getContent());

        source.setSummary(summary);
        sourceRepository.save(source);
        notebookRepository.touchLastActivity(source.getNotebook().getId());
        notebookContextService.evict();
        job.setResult(summary);

        return sourceRepository.findNotebookOwnerId(source.getId());
    }

    private UUID processNotebookSummary(SourceJob job) {
        UUID notebookId = job.getNotebook().getId();

        String context = notebookContextService.getContext(notebookId, 20);
        if (context.isBlank()) {
            throw new IllegalStateException("No sources available or content not yet indexed");
        }

        NotebookSummaryDTO summary = summarizeNotebook(context);
        String summaryText = summary != null ?
                summary.summary() :
                null;
        notebookRepository.updateSummary(
                notebookId,
                summaryText,
                summary != null ?
                        toJson(summary.topics()) :
                        null
        );
        job.setResult(summaryText);
        notebookContextService.evict();

        return notebookRepository.findOwnerId(notebookId);
    }

    private String summarizeInChunks(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        List<String> allChunks = splitIntoChunks(content);
        List<String> chunks = selectChunks(allChunks, summaryMaxChunks);
        if (chunks.size() == 1) {
            return callChat(chunks.getFirst());
        }

        int parallelism = Math.max(1, Math.min(summaryParallelism, chunks.size()));
        long startedAt = System.nanoTime();
        log.info(
                "Summarizing {} characters: {} chunks, {} selected, parallelism {}",
                content.length(),
                allChunks.size(),
                chunks.size(),
                parallelism
        );

        String[] summaries = new String[chunks.size()];
        AtomicInteger nextChunk = new AtomicInteger();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();

        List<CompletableFuture<Void>> workers = new ArrayList<>(parallelism);
        for (int i = 0; i < parallelism; i++) {
            workers.add(CompletableFuture.runAsync(
                    () -> {
                        int index;
                        while (failure.get() == null && (index = nextChunk.getAndIncrement()) < chunks.size()) {
                            try {
                                summaries[index] = callChat(chunks.get(index));
                            } catch (RuntimeException e) {
                                failure.compareAndSet(null, e);
                            }
                        }
                    }, taskExecutor
            ));
        }

        CompletableFuture.allOf(workers.toArray(CompletableFuture[]::new)).join();

        RuntimeException error = failure.get();
        if (error != null) {
            throw error;
        }

        StringBuilder intermediateSummaries = new StringBuilder();
        for (String summary : summaries) {
            intermediateSummaries.append(summary).append("\n\n");
        }
        String summary = callChat(intermediateSummaries.toString());
        log.info(
                "Source summary finished in {} ms using {} chunks",
                (System.nanoTime() - startedAt) / 1_000_000,
                chunks.size()
        );
        return summary;
    }

    private List<String> selectChunks(List<String> chunks, int maxChunks) {
        if (maxChunks <= 0 || chunks.size() <= maxChunks) {
            return chunks;
        }
        if (maxChunks == 1) {
            return List.of(chunks.getFirst());
        }

        // Keep a uniform sample across the whole document, including its
        // beginning and end. This bounds the number of AI calls for very
        // large sources without only reading the first pages.
        List<String> selected = new ArrayList<>(maxChunks);
        for (int i = 0; i < maxChunks; i++) {
            int index = (int) Math.round((double) i * (chunks.size() - 1) / (maxChunks - 1));
            selected.add(chunks.get(index));
        }
        return selected;
    }

    private List<String> splitIntoChunks(String content) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + SUMMARY_CHUNK_SIZE, content.length());
            chunks.add(content.substring(start, end));
            start = end;
        }
        return chunks;
    }

    private String callChat(String userContent) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(SOURCE_SUMMARY_SYSTEM_PROMPT),
                new UserMessage(userContent)
        ));
        return Objects.requireNonNull(chatModel.call(prompt).getResult()).getOutput().getText();
    }

    private NotebookSummaryDTO summarizeNotebook(String context) {
        String promptUser = "Analyze the following materials and write a high-level summary that captures the central theme and core concepts of this subject matter:\n\n";
        String promptUserFinal = "Analyze the following materials (which are partial summaries) and write a single, cohesive high-level summary that captures the central theme and core concepts of the entire subject matter:\n\n";

        if (context.length() > SUMMARY_CHUNK_SIZE) {
            StringBuilder intermediateSummaries = new StringBuilder();
            int start = 0;
            while (start < context.length()) {
                int end = Math.min(start + SUMMARY_CHUNK_SIZE, context.length());
                String chunk = context.substring(start, end);
                SummaryResponse chunkSummary = chatClient
                        .prompt()
                        .system(NOTEBOOK_SUMMARY_SYSTEM_PROMPT)
                        .user(promptUser + chunk)
                        .call()
                        .entity(SummaryResponse.class);
                if (chunkSummary != null && chunkSummary.summary() != null) {
                    intermediateSummaries.append(chunkSummary.summary()).append("\n\n");
                }
                start = end;
            }

            return chatClient
                    .prompt()
                    .system(NOTEBOOK_SUMMARY_TOPICS_SYSTEM_PROMPT)
                    .user(promptUserFinal + intermediateSummaries)
                    .call()
                    .entity(NotebookSummaryDTO.class);
        }

        return chatClient
                .prompt()
                .system(NOTEBOOK_SUMMARY_TOPICS_SYSTEM_PROMPT)
                .user(promptUser + context)
                .call()
                .entity(NotebookSummaryDTO.class);
    }

    private String toJson(List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(topics);
        } catch (Exception e) {
            log.error("Error serializing notebook summary topics", e);
            return null;
        }
    }

    private void publishCompletion(SourceJob job, UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        switch (job.getType()) {
            case EXTRACT, SOURCE_SUMMARY -> sseNotificationService.publish(
                    ownerId,
                    "source.updated",
                    sourceEventPayload(job, SourceStatus.READY)
            );
            case NOTEBOOK_SUMMARY -> sseNotificationService.publish(
                    ownerId, "summary.updated", Map.of(
                            "notebookId",
                            job.getNotebook().getId().toString(),
                            "summary",
                            job.getResult() != null ?
                                    job.getResult() :
                                    ""
                    )
            );
        }
    }

    private void publishFailure(SourceJob job) {
        if (job.getType() == SourceJob.JobType.NOTEBOOK_SUMMARY) {
            UUID ownerId = notebookRepository.findOwnerId(job.getNotebook().getId());
            if (ownerId == null) {
                return;
            }
            sseNotificationService.publish(
                    ownerId,
                    "summary.updated",
                    Map.of("notebookId", job.getNotebook().getId().toString(), "summary", "")
            );
            return;
        }

        UUID sourceId = job.getSource().getId();
        UUID ownerId = sourceRepository.findNotebookOwnerId(sourceId);
        if (ownerId == null) {
            return;
        }
        sseNotificationService.publish(ownerId, "source.updated", sourceEventPayload(job, SourceStatus.FAILED));
    }

    private Map<String, Object> sourceEventPayload(SourceJob job, SourceStatus fallbackStatus) {
        SourceStatus status = sourceRepository.findStatusById(job.getSource().getId()).orElse(fallbackStatus);
        return Map.of(
                "notebookId",
                job.getNotebook().getId().toString(),
                "sourceId",
                job.getSource().getId().toString(),
                "status",
                status.toString(),
                "summary",
                job.getResult() != null ?
                        job.getResult() :
                        "",
                "error",
                job.getErrorMessage() != null ?
                        job.getErrorMessage() :
                        ""
        );
    }

    private void deleteTempFile(SourceJob job) {
        if (job.getFilePath() == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(job.getFilePath()));
        } catch (IOException e) {
            log.warn("Could not delete temp file {}: {}", job.getFilePath(), e.getMessage());
        }
    }
}
