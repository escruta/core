package com.escruta.core.services;

import com.escruta.core.dtos.ExtractorResponse;
import com.escruta.core.dtos.SummaryResponse;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.Source;
import com.escruta.core.entities.SourceJob;
import com.escruta.core.entities.enums.SourceStatus;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.SourceJobRepository;
import com.escruta.core.repositories.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class SourceJobService {
    private final SourceJobRepository jobRepository;
    private final SourceRepository sourceRepository;
    private final NotebookRepository notebookRepository;
    private final HelperService helperService;
    private final AsyncVectorIndexingService asyncVectorIndexingService;
    private final RetrievalService retrievalService;
    private final ChatModel chatModel;
    private final SseNotificationService sseNotificationService;
    @Qualifier("taskExecutor")
    private final Executor taskExecutor;

    private static final int SUMMARY_CHUNK_SIZE = 50000;

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

    @Transactional
    public void startExtractJob(Source source, String filePath, String fileName) {
        SourceJob job = new SourceJob(source, source.getNotebook(), SourceJob.JobType.EXTRACT);
        job.setFilePath(filePath);
        job.setFileName(fileName);
        job = jobRepository.save(job);
        dispatchAfterCommit(job.getId());
    }

    @Transactional
    public SourceJob startSourceSummaryJob(Source source) {
        SourceJob existing = jobRepository.findFirstBySourceIdAndTypeAndStatusInOrderByCreatedAtDesc(
                source.getId(),
                SourceJob.JobType.SOURCE_SUMMARY,
                List.of(SourceJob.JobStatus.PENDING, SourceJob.JobStatus.PROCESSING)
        ).orElse(null);
        if (existing != null) {
            return existing;
        }
        SourceJob job = jobRepository.save(new SourceJob(
                source,
                source.getNotebook(),
                SourceJob.JobType.SOURCE_SUMMARY
        ));
        dispatchAfterCommit(job.getId());
        return job;
    }

    @Transactional
    public SourceJob startNotebookSummaryJob(Notebook notebook) {
        SourceJob existing = jobRepository.findFirstByNotebookIdAndTypeAndStatusInOrderByCreatedAtDesc(
                notebook.getId(),
                SourceJob.JobType.NOTEBOOK_SUMMARY,
                List.of(SourceJob.JobStatus.PENDING, SourceJob.JobStatus.PROCESSING)
        ).orElse(null);
        if (existing != null) {
            return existing;
        }
        SourceJob job = jobRepository.save(new SourceJob(null, notebook, SourceJob.JobType.NOTEBOOK_SUMMARY));
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
        taskExecutor.execute(() -> processJob(jobId));
    }

    public void processJob(UUID jobId) {
        SourceJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || isTerminal(job)) {
            return;
        }

        job.markAsProcessing();
        jobRepository.save(job);

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
                job.markAsFailed(e.getMessage());
                jobRepository.save(job);

                if (job.getType() == SourceJob.JobType.EXTRACT) {
                    sourceRepository.findById(job.getSource().getId()).ifPresent(source -> {
                        source.setStatus(SourceStatus.FAILED);
                        sourceRepository.save(source);
                    });
                }
                publishFailure(job);
            } catch (Exception recordError) {
                log.warn("Could not record failure for source job {}: {}", jobId, recordError.getMessage());
            }
        } finally {
            deleteTempFile(job);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumePendingJobs() {
        List<SourceJob> pending = jobRepository.findByStatusIn(List.of(
                SourceJob.JobStatus.PENDING,
                SourceJob.JobStatus.PROCESSING
        ));
        if (pending.isEmpty()) {
            return;
        }
        log.info("Resuming {} pending source jobs", pending.size());
        taskExecutor.execute(() -> pending.forEach(job -> processJob(job.getId())));
    }

    private boolean isTerminal(SourceJob job) {
        return job.getStatus() == SourceJob.JobStatus.COMPLETED || job.getStatus() == SourceJob.JobStatus.FAILED;
    }

    private UUID processExtract(SourceJob job) throws Exception {
        Source source = sourceRepository
                .findById(job.getSource().getId())
                .orElseThrow(() -> new IllegalStateException("Source no longer exists"));
        UUID notebookId = sourceRepository.findNotebookId(source.getId());

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

        asyncVectorIndexingService.indexSourceInVectorStore(
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

        return sourceRepository.findNotebookOwnerId(source.getId());
    }

    private UUID processSourceSummary(SourceJob job) throws Exception {
        Source source = sourceRepository
                .findById(job.getSource().getId())
                .orElseThrow(() -> new IllegalStateException("Source no longer exists"));

        String summary = summarizeInChunks(source.getContent(), SOURCE_SUMMARY_SYSTEM_PROMPT);

        source.setSummary(summary);
        sourceRepository.save(source);
        job.setResult(summary);

        return sourceRepository.findNotebookOwnerId(source.getId());
    }

    private UUID processNotebookSummary(SourceJob job) throws Exception {
        UUID notebookId = job.getNotebook().getId();
        if (!sourceRepository.existsByNotebookId(notebookId)) {
            throw new IllegalStateException("No sources available or content not yet indexed");
        }

        String query = "core concepts key ideas summary main topic definitions overview";
        List<Document> documents = retrievalService.getDocumentsForNotebook(notebookId, query, 20);
        if (documents.isEmpty()) {
            Thread.sleep(1000);
            documents = retrievalService.getDocumentsForNotebook(notebookId, query, 20);
            if (documents.isEmpty()) {
                throw new IllegalStateException("No sources available or content not yet indexed");
            }
        }

        String context = documents
                .stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
        if (context.isBlank()) {
            throw new IllegalStateException("No content available");
        }

        String summary = summarizeNotebook(context);
        notebookRepository.updateSummary(notebookId, summary);
        job.setResult(summary);

        return notebookRepository.findOwnerId(notebookId);
    }

    private String summarizeInChunks(String content, String systemPrompt) {
        if (content == null || content.isBlank()) {
            return "";
        }
        if (content.length() > SUMMARY_CHUNK_SIZE) {
            StringBuilder intermediateSummaries = new StringBuilder();
            int start = 0;
            while (start < content.length()) {
                int end = Math.min(start + SUMMARY_CHUNK_SIZE, content.length());
                String chunk = content.substring(start, end);
                intermediateSummaries.append(callChat(systemPrompt, chunk)).append("\n\n");
                start = end;
            }
            return callChat(systemPrompt, intermediateSummaries.toString());
        }
        return callChat(systemPrompt, content);
    }

    private String callChat(String systemPrompt, String userContent) {
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userContent)));
        return Objects.requireNonNull(chatModel.call(prompt).getResult()).getOutput().getText();
    }

    private String summarizeNotebook(String context) {
        String promptUser = "Analyze the following materials and write a high-level summary that captures the central theme and core concepts of this subject matter:\n\n";
        String promptUserFinal = "Analyze the following materials (which are partial summaries) and write a single, cohesive high-level summary that captures the central theme and core concepts of the entire subject matter:\n\n";

        if (context.length() > SUMMARY_CHUNK_SIZE) {
            StringBuilder intermediateSummaries = new StringBuilder();
            int start = 0;
            while (start < context.length()) {
                int end = Math.min(start + SUMMARY_CHUNK_SIZE, context.length());
                String chunk = context.substring(start, end);
                SummaryResponse chunkSummary = ChatClient
                        .create(chatModel)
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

            SummaryResponse finalSummary = ChatClient
                    .create(chatModel)
                    .prompt()
                    .system(NOTEBOOK_SUMMARY_SYSTEM_PROMPT)
                    .user(promptUserFinal + intermediateSummaries)
                    .call()
                    .entity(SummaryResponse.class);
            return finalSummary != null ?
                    finalSummary.summary() :
                    null;
        }

        SummaryResponse summary = ChatClient
                .create(chatModel)
                .prompt()
                .system(NOTEBOOK_SUMMARY_SYSTEM_PROMPT)
                .user(promptUser + context)
                .call()
                .entity(SummaryResponse.class);
        return summary != null ?
                summary.summary() :
                null;
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
        SourceStatus status = sourceRepository
                .findById(job.getSource().getId())
                .map(Source::getStatus)
                .orElse(fallbackStatus);
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
