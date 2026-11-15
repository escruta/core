package com.escruta.core.services;

import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.Source;
import com.escruta.core.entities.SourceChunk;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.SourceChunkRepository;
import com.escruta.core.repositories.SourceRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-request toolset exposed to the chat model so conversations execute as an
 * observable, multistep research/tutoring agent grounded in notebook sources.
 */
public class NotebookAgentTools {
    private static final int SEARCH_TOP_K = 6;
    private static final int OPENING_CHUNKS_LIMIT = 3;

    private final UUID notebookId;
    private final List<UUID> selectedSourceIds;
    private final RetrievalService retrievalService;
    private final SourceRepository sourceRepository;
    private final SourceChunkRepository chunkRepository;
    private final NotebookRepository notebookRepository;
    private final NotebookContextService notebookContextService;
    private final AgentTraceRecorder traceRecorder;
    private final Map<String, Document> collectedDocuments = new LinkedHashMap<>();

    public NotebookAgentTools(
            UUID notebookId,
            @Nullable List<UUID> selectedSourceIds,
            RetrievalService retrievalService,
            SourceRepository sourceRepository,
            SourceChunkRepository chunkRepository,
            NotebookRepository notebookRepository,
            NotebookContextService notebookContextService,
            AgentTraceRecorder traceRecorder
    ) {
        this.notebookId = notebookId;
        this.selectedSourceIds = selectedSourceIds == null ?
                List.of() :
                List.copyOf(selectedSourceIds);
        this.retrievalService = retrievalService;
        this.sourceRepository = sourceRepository;
        this.chunkRepository = chunkRepository;
        this.notebookRepository = notebookRepository;
        this.notebookContextService = notebookContextService;
        this.traceRecorder = traceRecorder;
    }

    @Tool(description = "Search the selected notebook sources for passages relevant to a specific query. " + "Use focused keywords, concepts, or questions. You can call this multiple times with different queries.")
    public String searchSources(
            @ToolParam(description = "Focused search query with keywords or concepts") String query,
            @ToolParam(description = "Brief explanation of what you are looking for and why") @Nullable String rationale
    ) {
        String cleanQuery = query == null ?
                "" :
                query.trim();
        String cleanRationale = cleanText(rationale);
        String stepId = traceRecorder.startStep(
                "search",
                "Searching sources",
                cleanQuery.isEmpty() ?
                        "All selected sources" :
                        cleanQuery,
                cleanRationale
        );

        try {
            List<Document> docs = retrievalService.search(notebookId, selectedSourceIds, cleanQuery, SEARCH_TOP_K);
            recordDocuments(docs);

            List<String> sourceTitles = extractDistinctTitles(docs);
            String resultSummary = docs.isEmpty() ?
                    "No matching passages found" :
                    "Found " + docs.size() + " passage" + (docs.size() == 1 ?
                            "" :
                            "s");
            traceRecorder.completeStep(stepId, resultSummary, sourceTitles);

            if (docs.isEmpty()) {
                return "No matching passages were found for query: \"" + cleanQuery + "\". " + "Try broader keywords or use getSourceOverview to inspect a source directly.";
            }

            StringBuilder out = new StringBuilder();
            out.append("Found ").append(docs.size()).append(" relevant passage(s):\n\n");
            for (Document doc : docs) {
                String title = String.valueOf(doc.getMetadata().getOrDefault("title", "Untitled"));
                out
                        .append("Source ID: ")
                        .append(doc.getId())
                        .append(" | Title: ")
                        .append(title)
                        .append("\n")
                        .append(doc.getText())
                        .append("\n")
                        .append("---------------------\n");
            }
            return out.toString();
        } catch (Exception e) {
            traceRecorder.failStep(stepId, "Search failed");
            return "Error searching sources: " + e.getMessage();
        }
    }

    @Tool(description = "Read the summary and opening sections of a specific source by its UUID. " + "Use this to understand the structure, main ideas, or overall content of a source.")
    public String getSourceOverview(
            @ToolParam(description = "The UUID of the source to inspect") String sourceId,
            @ToolParam(description = "Brief explanation of why you are reading this source") @Nullable String rationale
    ) {
        String cleanId = sourceId == null ?
                "" :
                sourceId.trim();
        String cleanRationale = cleanText(rationale);
        String stepId = traceRecorder.startStep("read", "Reading source", cleanId, cleanRationale);

        UUID parsedId;
        try {
            parsedId = UUID.fromString(cleanId);
        } catch (IllegalArgumentException e) {
            traceRecorder.failStep(stepId, "Invalid source ID");
            return "Invalid sourceId format. Please pass a valid UUID from the available sources list.";
        }

        if (!selectedSourceIds.isEmpty() && !selectedSourceIds.contains(parsedId)) {
            traceRecorder.failStep(stepId, "Source not in selection");
            return "This source is not among the currently selected sources.";
        }

        Source source = sourceRepository.findByIdAndNotebookId(parsedId, notebookId).orElse(null);
        if (source == null) {
            traceRecorder.failStep(stepId, "Source not found");
            return "Source not found in this notebook.";
        }

        String title = source.getTitle() != null && !source.getTitle().isBlank() ?
                source.getTitle() :
                "Untitled";

        // Update step detail to show the human-friendly title.
        List<SourceChunk> openingChunks = chunkRepository.findByNotebookIdAndSourceIdInOrderByChunkIndexAsc(
                notebookId,
                List.of(parsedId),
                PageRequest.of(0, OPENING_CHUNKS_LIMIT)
        );
        List<Document> openingDocs = openingChunks.stream().map(NotebookAgentTools::chunkToDocument).toList();
        recordDocuments(openingDocs);

        StringBuilder out = new StringBuilder();
        out.append("Source: ").append(title).append(" (").append(source.getType()).append(")\n");
        boolean hasSummary = source.getSummary() != null && !source.getSummary().isBlank();
        if (hasSummary) {
            out.append("\nSummary:\n").append(source.getSummary().trim()).append("\n");
        }
        if (!openingDocs.isEmpty()) {
            out.append("\nOpening passages:\n");
            for (Document doc : openingDocs) {
                out
                        .append("Source ID: ")
                        .append(doc.getId())
                        .append(" | Title: ")
                        .append(title)
                        .append("\n")
                        .append(doc.getText())
                        .append("\n")
                        .append("---------------------\n");
            }
        }

        String summaryStatus = hasSummary ?
                "Loaded summary & opening passages" :
                (!openingDocs.isEmpty() ?
                        "Loaded opening passages" :
                        "No content available yet");
        // Emits updated step with title as detail
        updateStepDetailAndComplete(stepId, title, summaryStatus, List.of(title));
        return out.toString();
    }

    @Tool(description = "Get the high-level overview summary and main topics of the entire notebook.")
    public String getNotebookOverview(
            @ToolParam(description = "Brief explanation of why you are checking the notebook overview")
            @Nullable String rationale
    ) {
        String stepId = traceRecorder.startStep("context", "Checking notebook overview", null, cleanText(rationale));
        try {
            Notebook notebook = notebookRepository.findById(notebookId).orElse(null);
            String summary = notebook != null ?
                    notebook.getSummary() :
                    null;
            String topics = notebook != null ?
                    notebook.getSummaryTopics() :
                    null;
            if (summary != null && !summary.isBlank()) {
                traceRecorder.completeStep(stepId, "Loaded notebook summary", List.of());
                StringBuilder sb = new StringBuilder("Notebook summary:\n").append(summary.trim());
                if (topics != null && !topics.isBlank()) {
                    sb.append("\n\nMain topics: ").append(topics);
                }
                return sb.toString();
            }

            String fallback = notebookContextService.getContext(notebookId, 5);
            if (fallback != null && !fallback.isBlank()) {
                traceRecorder.completeStep(stepId, "Loaded key excerpts", List.of());
                return "Notebook key excerpts:\n" + fallback;
            }

            traceRecorder.completeStep(stepId, "No overview available", List.of());
            return "No notebook overview summary is available yet.";
        } catch (Exception e) {
            traceRecorder.failStep(stepId, "Failed to load overview");
            return "Could not load notebook overview.";
        }
    }

    public synchronized List<Document> getCollectedDocuments() {
        return List.copyOf(collectedDocuments.values());
    }

    private void updateStepDetailAndComplete(String stepId, String titleDetail, String result, List<String> sources) {
        traceRecorder.completeStepWithDetail(stepId, titleDetail, result, sources);
    }

    private synchronized void recordDocuments(List<Document> docs) {
        for (Document doc : docs) {
            collectedDocuments.putIfAbsent(doc.getId(), doc);
        }
    }

    private static List<String> extractDistinctTitles(List<Document> docs) {
        Set<String> titles = new LinkedHashSet<>();
        for (Document doc : docs) {
            Object rawTitle = doc.getMetadata().get("title");
            if (rawTitle != null) {
                String title = String.valueOf(rawTitle).trim();
                if (!title.isEmpty() && !"null".equals(title)) {
                    titles.add(title);
                    if (titles.size() >= 4) {
                        break;
                    }
                }
            }
        }
        return List.copyOf(titles);
    }

    private static @Nullable String cleanText(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ?
                null :
                trimmed;
    }

    private static Document chunkToDocument(SourceChunk chunk) {
        return getDocument(chunk);
    }

    @NonNull
    static Document getDocument(SourceChunk chunk) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceId", chunk.getSourceId().toString());
        metadata.put("notebookId", chunk.getNotebookId().toString());
        metadata.put(
                "title",
                chunk.getTitle() != null ?
                        chunk.getTitle() :
                        "Untitled"
        );
        metadata.put(
                "link",
                chunk.getLink() != null ?
                        chunk.getLink() :
                        ""
        );
        metadata.put("chunkIndex", String.valueOf(chunk.getChunkIndex()));
        return new Document(chunk.getId().toString(), chunk.getContent(), metadata);
    }
}
