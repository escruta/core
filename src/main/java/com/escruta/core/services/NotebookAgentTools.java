package com.escruta.core.services;

import com.escruta.core.dtos.source.SourceResponseDTO;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.Source;
import com.escruta.core.entities.SourceChunk;
import com.escruta.core.entities.SourceGroup;
import com.escruta.core.entities.enums.SourceStatus;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.SourceChunkRepository;
import com.escruta.core.repositories.SourceGroupRepository;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-request toolset exposed to the chat model so conversations execute as an
 * observable, multistep research/tutoring agent grounded in notebook sources.
 */
public class NotebookAgentTools {
    private static final int SEARCH_TOP_K = 6;
    private static final int EXACT_SEARCH_TOP_K = 8;
    private static final int OPENING_CHUNKS_LIMIT = 3;
    private static final int DEFAULT_READ_CHUNKS_COUNT = 4;
    private static final int MAX_READ_CHUNKS_COUNT = 8;
    private static final int DEFAULT_SURROUNDING_WINDOW = 2;
    private static final int MAX_SURROUNDING_WINDOW = 4;
    private static final int MAX_LIST_SOURCES = 50;
    private static final int MAX_EXACT_PHRASE_LENGTH = 200;

    private final UUID notebookId;
    private final List<UUID> selectedSourceIds;
    private final RetrievalService retrievalService;
    private final SourceRepository sourceRepository;
    private final SourceChunkRepository chunkRepository;
    private final NotebookRepository notebookRepository;
    private final NotebookContextService notebookContextService;
    private final @Nullable SourceGroupRepository sourceGroupRepository;
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
            @Nullable SourceGroupRepository sourceGroupRepository,
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
        this.sourceGroupRepository = sourceGroupRepository;
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
        String stepId = traceRecorder.startStep(
                "search",
                "Searching sources",
                cleanQuery.isEmpty() ?
                        "All selected sources" :
                        cleanQuery,
                cleanText(rationale)
        );

        try {
            List<Document> docs = retrievalService.search(notebookId, selectedSourceIds, cleanQuery, SEARCH_TOP_K);
            return finishSearchStep(
                    stepId,
                    docs,
                    "passage",
                    "passages",
                    "No matching passages found",
                    "No matching passages were found for query: \"" + cleanQuery + "\". " + "Try broader keywords, searchExactMatch for literal terms/phrases, or getSourceOverview to inspect a source directly.",
                    "Found " + docs.size() + " relevant passage(s):"
            );
        } catch (Exception e) {
            traceRecorder.failStep(stepId, "Search failed");
            return "Error searching sources: " + e.getMessage();
        }
    }

    @Tool(description = "Search the selected notebook sources for an exact literal phrase, acronym, code snippet, symbol, formula, or proper name (case-insensitive substring match). " + "Use this when keyword search misses exact quotes, short terms, identifiers, or punctuation.")
    public String searchExactMatch(
            @ToolParam(description = "Exact phrase, term, code identifier, or quote to match literally") String phrase,
            @ToolParam(description = "Optional Document UUID of a specific source to search within")
            @Nullable String sourceId,
            @ToolParam(description = "Brief explanation of what exact match you are looking for")
            @Nullable String rationale
    ) {
        String cleanPhrase = phrase == null ?
                "" :
                phrase.trim();
        String stepId = traceRecorder.startStep(
                "search",
                "Searching exact match",
                cleanPhrase.isEmpty() ?
                        "Exact search" :
                        cleanPhrase,
                cleanText(rationale)
        );

        if (cleanPhrase.isEmpty()) {
            traceRecorder.failStep(stepId, "Empty search phrase");
            return "Search phrase cannot be empty.";
        }
        if (cleanPhrase.length() > MAX_EXACT_PHRASE_LENGTH) {
            traceRecorder.failStep(stepId, "Phrase too long");
            return "Search phrase must be at most " + MAX_EXACT_PHRASE_LENGTH + " characters.";
        }

        List<UUID> scopedIds = selectedSourceIds;
        String cleanSourceId = cleanText(sourceId);
        if (cleanSourceId != null) {
            ParsedIdResult parsed = parseScopedSourceId(
                    stepId,
                    cleanSourceId,
                    "Invalid sourceId format. Please pass a valid Document UUID."
            );
            if (parsed.errorMessage() != null) {
                return parsed.errorMessage();
            }
            assert parsed.id() != null;
            scopedIds = List.of(parsed.id());
        }

        try {
            PageRequest page = PageRequest.of(0, EXACT_SEARCH_TOP_K);
            List<SourceChunk> chunks = scopedIds.isEmpty() ?
                    chunkRepository.findExactByNotebook(notebookId, cleanPhrase, page) :
                    chunkRepository.findExactByNotebookAndSources(notebookId, scopedIds, cleanPhrase, page);

            List<Document> docs = chunks.stream().map(NotebookAgentTools::chunkToDocument).toList();
            return finishSearchStep(
                    stepId,
                    docs,
                    "exact match",
                    "exact matches",
                    "No exact matches found",
                    "No exact literal matches were found for: \"" + cleanPhrase + "\".",
                    "Found " + docs.size() + " exact match(es) for \"" + cleanPhrase + "\":"
            );
        } catch (Exception e) {
            traceRecorder.failStep(stepId, "Exact search failed");
            return "Error searching exact match: " + e.getMessage();
        }
    }

    @Tool(description = "Read the summary and opening sections of a specific source by its Document UUID. " + "Use this to understand the structure, main ideas, or overall content of a source.")
    public String getSourceOverview(
            @ToolParam(description = "The Document UUID of the source to inspect") String sourceId,
            @ToolParam(description = "Brief explanation of why you are reading this source") @Nullable String rationale
    ) {
        String cleanId = sourceId == null ?
                "" :
                sourceId.trim();
        String stepId = traceRecorder.startStep("read", "Reading source", cleanId, cleanText(rationale));

        ScopedSourceResult resolved = resolveScopedSource(
                stepId,
                cleanId,
                "Invalid sourceId format. Please pass a valid UUID from the available sources list."
        );
        if (resolved.errorMessage() != null) {
            return resolved.errorMessage();
        }
        Source source = resolved.source();
        assert source != null;
        UUID parsedId = source.getId();
        String title = resolved.title();

        long totalChunks = chunkRepository.countByNotebookIdAndSourceId(notebookId, parsedId);
        List<SourceChunk> openingChunks = chunkRepository.findByNotebookIdAndSourceIdInOrderByChunkIndexAsc(
                notebookId,
                List.of(parsedId),
                PageRequest.of(0, OPENING_CHUNKS_LIMIT)
        );
        List<Document> openingDocs = openingChunks.stream().map(NotebookAgentTools::chunkToDocument).toList();
        recordDocuments(openingDocs);

        StringBuilder out = new StringBuilder();
        out
                .append("Source: ")
                .append(title)
                .append(" (")
                .append(source.getType())
                .append(") | Document ID: ")
                .append(parsedId);
        if (totalChunks > 0) {
            out.append(" | Total chunks: ").append(totalChunks);
        }
        out.append("\n");

        boolean hasSummary = source.getSummary() != null && !source.getSummary().isBlank();
        if (hasSummary) {
            out.append("\nSummary:\n").append(source.getSummary().trim()).append("\n");
        }
        if (!openingDocs.isEmpty()) {
            out.append("\nOpening passages:\n");
            appendDocuments(out, openingDocs, null);
        }

        String summaryStatus = hasSummary ?
                "Loaded summary & opening passages" :
                (!openingDocs.isEmpty() ?
                        "Loaded opening passages" :
                        "No content available yet");
        updateStepDetailAndComplete(stepId, title, summaryStatus, List.of(title));
        return out.toString();
    }

    @Tool(description = "Read a sequential range of passages from a specific source by its Document UUID and 0-based starting chunk index. " + "Use this after getSourceOverview or searchSources to read further into a document or inspect later sections.")
    public String readSourceChunks(
            @ToolParam(description = "The Document UUID of the source to read") String sourceId,
            @ToolParam(description = "0-based starting chunk index (for example 0, 3, or 10)")
            @Nullable Integer startChunkIndex,
            @ToolParam(description = "Number of consecutive chunks to read (1 to 8, default 4)")
            @Nullable Integer count,
            @ToolParam(description = "Brief explanation of why you are reading these chunks") @Nullable String rationale
    ) {
        String cleanId = sourceId == null ?
                "" :
                sourceId.trim();
        int start = startChunkIndex == null ?
                0 :
                Math.max(0, startChunkIndex);
        int limit = count == null ?
                DEFAULT_READ_CHUNKS_COUNT :
                Math.clamp(count, 1, MAX_READ_CHUNKS_COUNT);
        int end = start + limit - 1;

        String stepId = traceRecorder.startStep(
                "read",
                "Reading source chunks",
                cleanId + " [" + start + "-" + end + "]",
                cleanText(rationale)
        );

        ScopedSourceResult resolved = resolveScopedSource(
                stepId,
                cleanId,
                "Invalid sourceId format. Please pass a valid Document UUID."
        );
        if (resolved.errorMessage() != null) {
            return resolved.errorMessage();
        }
        assert resolved.source() != null;
        UUID parsedId = resolved.source().getId();
        String title = resolved.title();
        long totalChunks = chunkRepository.countByNotebookIdAndSourceId(notebookId, parsedId);

        List<SourceChunk> chunks = chunkRepository.findByNotebookIdAndSourceIdAndChunkIndexBetweenOrderByChunkIndexAsc(notebookId,
                parsedId,
                start,
                end
        );
        List<Document> docs = chunks.stream().map(NotebookAgentTools::chunkToDocument).toList();
        recordDocuments(docs);

        if (docs.isEmpty()) {
            String message = totalChunks == 0 ?
                    "This source has no indexed chunks yet." :
                    "No chunks found in range [" + start + "-" + end + "]. Valid chunk indices for \"" + title + "\" are 0 to " + (totalChunks - 1) + ".";
            updateStepDetailAndComplete(stepId, title, "No chunks in range", List.of(title));
            return message;
        }

        int firstIdx = chunks.getFirst().getChunkIndex();
        int lastIdx = chunks.getLast().getChunkIndex();
        String rangeLabel = firstIdx == lastIdx ?
                "chunk " + firstIdx :
                "chunks " + firstIdx + "-" + lastIdx;
        String detailLabel = title + " (" + rangeLabel + ")";
        String resultLabel = totalChunks > 0 ?
                "Loaded " + docs.size() + " of " + totalChunks + " chunks" :
                "Loaded " + docs.size() + " chunk" + (docs.size() == 1 ?
                        "" :
                        "s");
        updateStepDetailAndComplete(stepId, detailLabel, resultLabel, List.of(title));

        StringBuilder out = new StringBuilder();
        out
                .append("Source: ")
                .append(title)
                .append(" | Document ID: ")
                .append(parsedId)
                .append(" | Showing ")
                .append(rangeLabel);
        if (totalChunks > 0) {
            out.append(" (total chunks: ").append(totalChunks).append(")");
        }
        out.append("\n\n");
        appendDocuments(out, docs, null);
        return out.toString();
    }

    @Tool(description = "Read passages immediately before and after a specific passage UUID (shown as 'Source ID: <uuid>' in search results) to recover full surrounding context.")
    public String getSurroundingChunks(
            @ToolParam(description = "The passage UUID (Source ID from a search or read result)") String passageId,
            @ToolParam(description = "Number of chunks to include before and after (1 to 4, default 2)")
            @Nullable Integer window,
            @ToolParam(description = "Brief explanation of why you need surrounding context") @Nullable String rationale
    ) {
        String cleanId = passageId == null ?
                "" :
                passageId.trim();
        int clampedWindow = window == null ?
                DEFAULT_SURROUNDING_WINDOW :
                Math.clamp(window, 1, MAX_SURROUNDING_WINDOW);
        String stepId = traceRecorder.startStep("read", "Reading surrounding context", cleanId, cleanText(rationale));

        UUID parsedChunkId;
        try {
            parsedChunkId = UUID.fromString(cleanId);
        } catch (IllegalArgumentException e) {
            traceRecorder.failStep(stepId, "Invalid passage ID");
            return "Invalid passageId format. Please pass a valid passage UUID from a previous result.";
        }

        SourceChunk anchor = chunkRepository.findByIdAndNotebookId(parsedChunkId, notebookId).orElse(null);
        if (anchor == null) {
            traceRecorder.failStep(stepId, "Passage not found");
            return "Passage not found in this notebook.";
        }

        if (!selectedSourceIds.isEmpty() && !selectedSourceIds.contains(anchor.getSourceId())) {
            traceRecorder.failStep(stepId, "Source not in selection");
            return "This passage belongs to a source outside the currently selected sources.";
        }

        int from = Math.max(0, anchor.getChunkIndex() - clampedWindow);
        int to = anchor.getChunkIndex() + clampedWindow;
        List<SourceChunk> chunks = chunkRepository.findByNotebookIdAndSourceIdAndChunkIndexBetweenOrderByChunkIndexAsc(notebookId,
                anchor.getSourceId(),
                from,
                to
        );
        List<Document> docs = chunks.stream().map(NotebookAgentTools::chunkToDocument).toList();
        recordDocuments(docs);

        String title = resolveTitle(anchor.getTitle());
        int firstIdx = chunks.isEmpty() ?
                anchor.getChunkIndex() :
                chunks.getFirst().getChunkIndex();
        int lastIdx = chunks.isEmpty() ?
                anchor.getChunkIndex() :
                chunks.getLast().getChunkIndex();

        updateStepDetailAndComplete(
                stepId,
                title + " (around chunk " + anchor.getChunkIndex() + ")",
                "Loaded " + docs.size() + " passage" + (docs.size() == 1 ?
                        "" :
                        "s"),
                List.of(title)
        );

        StringBuilder out = new StringBuilder();
        out
                .append("Surrounding passages from \"")
                .append(title)
                .append("\" | Document ID: ")
                .append(anchor.getSourceId())
                .append(" | Chunks ")
                .append(firstIdx)
                .append("-")
                .append(lastIdx)
                .append(" (anchor chunk: ")
                .append(anchor.getChunkIndex())
                .append(")\n\n");
        appendDocuments(out, docs, parsedChunkId.toString());
        return out.toString();
    }

    @Tool(description = "List available sources and source groups in the current scope, optionally filtering by title/URL keyword or source group UUID. " + "Use this when the notebook has many sources or you want to discover sources organized in groups.")
    public String listSources(
            @ToolParam(description = "Optional keyword to filter sources by title or URL") @Nullable String query,
            @ToolParam(description = "Optional UUID of a source group to filter by") @Nullable String groupId,
            @ToolParam(description = "Brief explanation of why you are listing sources") @Nullable String rationale
    ) {
        String cleanQuery = cleanText(query);
        String cleanGroupId = cleanText(groupId);
        String detail = cleanQuery != null ?
                cleanQuery :
                (cleanGroupId != null ?
                        "Group " + cleanGroupId :
                        "All scoped sources");
        String stepId = traceRecorder.startStep("context", "Listing sources", detail, cleanText(rationale));

        UUID parsedGroupId = null;
        if (cleanGroupId != null) {
            try {
                parsedGroupId = UUID.fromString(cleanGroupId);
            } catch (IllegalArgumentException e) {
                traceRecorder.failStep(stepId, "Invalid group ID");
                return "Invalid groupId format. Please pass a valid UUID or omit groupId.";
            }
        }

        try {
            List<SourceResponseDTO> all = sourceRepository.findSummariesByNotebookId(notebookId);
            Set<UUID> allowed = selectedSourceIds.isEmpty() ?
                    null :
                    Set.copyOf(selectedSourceIds);

            List<SourceResponseDTO> scoped = all
                    .stream()
                    .filter(s -> s.status() == SourceStatus.READY)
                    .filter(s -> allowed == null || allowed.contains(s.id()))
                    .toList();

            Map<UUID, String> groupNames = new LinkedHashMap<>();
            List<SourceGroup> groups = sourceGroupRepository != null ?
                    sourceGroupRepository.findByNotebookIdOrderByCreatedAtDesc(notebookId) :
                    List.of();
            for (SourceGroup group : groups) {
                groupNames.put(group.getId(), group.getTitle());
            }

            final UUID filterGroupId = parsedGroupId;
            final String lowerQuery = cleanQuery != null ?
                    cleanQuery.toLowerCase(Locale.ROOT) :
                    null;

            List<SourceResponseDTO> filtered = scoped
                    .stream()
                    .filter(s -> filterGroupId == null || filterGroupId.equals(s.groupId()))
                    .filter(s -> {
                        if (lowerQuery == null) {
                            return true;
                        }
                        String title = s.title() != null ?
                                s.title().toLowerCase(Locale.ROOT) :
                                "";
                        String link = s.link() != null ?
                                s.link().toLowerCase(Locale.ROOT) :
                                "";
                        return title.contains(lowerQuery) || link.contains(lowerQuery);
                    })
                    .toList();

            int shownCount = Math.min(filtered.size(), MAX_LIST_SOURCES);
            traceRecorder.completeStep(
                    stepId,
                    "Found " + filtered.size() + " source" + (filtered.size() == 1 ?
                            "" :
                            "s"),
                    List.of()
            );

            StringBuilder out = new StringBuilder();
            if (!groups.isEmpty()) {
                out.append("Source groups (").append(groups.size()).append("):\n");
                for (SourceGroup group : groups) {
                    out
                            .append("- Group ID: ")
                            .append(group.getId())
                            .append(" | Name: \"")
                            .append(group.getTitle())
                            .append("\"\n");
                }
                out.append("\n");
            }

            if (filtered.isEmpty()) {
                out
                        .append("No matching sources found in scope (total scoped ready sources: ")
                        .append(scoped.size())
                        .append(").");
                return out.toString();
            }

            out.append("Matching sources (").append(filtered.size()).append("):\n");
            for (int i = 0; i < shownCount; i++) {
                SourceResponseDTO s = filtered.get(i);
                out
                        .append("- Document ID: ")
                        .append(s.id())
                        .append(" | Title: \"")
                        .append(resolveTitle(s.title()))
                        .append("\" | Type: ")
                        .append(s.type());
                if (s.groupId() != null) {
                    String groupName = groupNames.getOrDefault(s.groupId(), s.groupId().toString());
                    out.append(" | Group: \"").append(groupName).append("\" (").append(s.groupId()).append(")");
                }
                if (s.link() != null && !s.link().isBlank()) {
                    out.append(" | Link: ").append(s.link());
                }
                out.append("\n");
            }
            if (filtered.size() > shownCount) {
                out
                        .append("... and ")
                        .append(filtered.size() - shownCount)
                        .append(" more. Narrow your query or groupId to inspect the rest.\n");
            }
            return out.toString();
        } catch (Exception e) {
            traceRecorder.failStep(stepId, "Failed to list sources");
            return "Error listing sources: " + e.getMessage();
        }
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

    @Tool(description = "Evaluate a mathematical expression deterministically with exact decimal arithmetic and scientific functions. " + "Supports +, -, *, /, % (modulo), ^ (power), parentheses, constants (pi, e, tau), and functions: " + "sqrt, cbrt, abs, round, floor, ceil, sign, sin, cos, tan, asin, acos, atan, atan2, sinh, cosh, tanh, " + "ln, log, log10, log2, exp, pow, mod, min, max, sum, avg, mean, factorial, deg, rad. " + "Always use this instead of mental arithmetic for calculations.")
    public String calculate(
            @ToolParam(description = "Mathematical expression to evaluate, e.g. '(1250 * 0.18) / sqrt(16)' or 'avg(12, 15, 21)'")
            String expression,
            @ToolParam(description = "Brief explanation of what this calculation computes") @Nullable String rationale
    ) {
        String cleanExpr = expression == null ?
                "" :
                expression.trim();
        String stepId = traceRecorder.startStep(
                "calculate",
                "Calculating",
                cleanExpr.isEmpty() ?
                        "Expression" :
                        cleanExpr,
                cleanText(rationale)
        );
        try {
            MathExpressionEvaluator.EvaluationResult eval = MathExpressionEvaluator.evaluate(cleanExpr);
            traceRecorder.completeStep(stepId, "= " + eval.formatted(), List.of());
            return "Expression: " + cleanExpr + "\nResult: " + eval.formatted();
        } catch (Exception e) {
            traceRecorder.failStep(stepId, e.getMessage());
            return "Could not evaluate expression: " + e.getMessage();
        }
    }

    public synchronized List<Document> getCollectedDocuments() {
        return List.copyOf(collectedDocuments.values());
    }

    private ParsedIdResult parseScopedSourceId(String stepId, String cleanId, String invalidIdMessage) {
        UUID parsedId;
        try {
            parsedId = UUID.fromString(cleanId);
        } catch (IllegalArgumentException e) {
            traceRecorder.failStep(stepId, "Invalid source ID");
            return new ParsedIdResult(null, invalidIdMessage);
        }

        if (!selectedSourceIds.isEmpty() && !selectedSourceIds.contains(parsedId)) {
            traceRecorder.failStep(stepId, "Source not in selection");
            return new ParsedIdResult(null, "This source is not among the currently selected sources.");
        }
        return new ParsedIdResult(parsedId, null);
    }

    private ScopedSourceResult resolveScopedSource(String stepId, String cleanId, String invalidIdMessage) {
        ParsedIdResult parsed = parseScopedSourceId(stepId, cleanId, invalidIdMessage);
        if (parsed.errorMessage() != null) {
            return new ScopedSourceResult(null, "Untitled", parsed.errorMessage());
        }

        Source source = sourceRepository.findByIdAndNotebookId(parsed.id(), notebookId).orElse(null);
        if (source == null) {
            traceRecorder.failStep(stepId, "Source not found");
            return new ScopedSourceResult(null, "Untitled", "Source not found in this notebook.");
        }
        return new ScopedSourceResult(source, resolveTitle(source.getTitle()), null);
    }

    private String finishSearchStep(
            String stepId,
            List<Document> docs,
            String nounSingular,
            String nounPlural,
            String emptyStepResult,
            String emptyResponse,
            String headerPrefix
    ) {
        recordDocuments(docs);

        List<String> sourceTitles = extractDistinctTitles(docs);
        String resultSummary = docs.isEmpty() ?
                emptyStepResult :
                "Found " + docs.size() + " " + (docs.size() == 1 ?
                        nounSingular :
                        nounPlural);
        traceRecorder.completeStep(stepId, resultSummary, sourceTitles);

        if (docs.isEmpty()) {
            return emptyResponse;
        }

        StringBuilder out = new StringBuilder();
        out.append(headerPrefix).append("\n\n");
        appendDocuments(out, docs, null);
        return out.toString();
    }

    private void updateStepDetailAndComplete(String stepId, String titleDetail, String result, List<String> sources) {
        traceRecorder.completeStepWithDetail(stepId, titleDetail, result, sources);
    }

    private synchronized void recordDocuments(List<Document> docs) {
        for (Document doc : docs) {
            collectedDocuments.putIfAbsent(doc.getId(), doc);
        }
    }

    private static void appendDocuments(StringBuilder out, List<Document> docs, @Nullable String anchorPassageId) {
        for (Document doc : docs) {
            String title = String.valueOf(doc.getMetadata().getOrDefault("title", "Untitled"));
            String parentSourceId = String.valueOf(doc.getMetadata().getOrDefault("sourceId", ""));
            String chunkIndex = String.valueOf(doc.getMetadata().getOrDefault("chunkIndex", "?"));
            out
                    .append("Source ID: ")
                    .append(doc.getId())
                    .append(" | Document ID: ")
                    .append(parentSourceId)
                    .append(" | Chunk: ")
                    .append(chunkIndex)
                    .append(" | Title: ")
                    .append(title);
            if (anchorPassageId != null && anchorPassageId.equals(doc.getId())) {
                out.append(" [ANCHOR PASSAGE]");
            }
            out.append("\n").append(doc.getText()).append("\n").append("---------------------\n");
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

    private static String resolveTitle(@Nullable String title) {
        return title != null && !title.isBlank() ?
                title :
                "Untitled";
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
        metadata.put("title", resolveTitle(chunk.getTitle()));
        metadata.put(
                "link",
                chunk.getLink() != null ?
                        chunk.getLink() :
                        ""
        );
        metadata.put("chunkIndex", String.valueOf(chunk.getChunkIndex()));
        return new Document(chunk.getId().toString(), chunk.getContent(), metadata);
    }

    private record ParsedIdResult(
            @Nullable
            UUID id,
            @Nullable
            String errorMessage
    ) {
    }

    private record ScopedSourceResult(
            @Nullable
            Source source,
            String title,
            @Nullable
            String errorMessage
    ) {
    }
}
