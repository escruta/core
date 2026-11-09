package com.escruta.core.services;

import com.escruta.core.entities.SourceChunk;
import com.escruta.core.repositories.SourceChunkRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetrievalService {
    private static final int FALLBACK_SCAN_SIZE = 200;

    private final SourceChunkRepository chunkRepository;

    public CustomQuestionAnswerAdvisor getQuestionAnswerAdvisor(UUID notebookId, List<UUID> selectedSourceIds) {
        return CustomQuestionAnswerAdvisor
                .builder(this, notebookId)
                .selectedSourceIds(selectedSourceIds)
                .topK(5)
                .build();
    }

    public List<Document> search(UUID notebookId, List<UUID> selectedSourceIds, String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }

        List<SourceChunk> chunks;
        try {
            if (selectedSourceIds != null && !selectedSourceIds.isEmpty()) {
                chunks = chunkRepository.searchByNotebookAndSources(notebookId, selectedSourceIds, query, limit);
            } else {
                chunks = chunkRepository.searchByNotebook(notebookId, query, limit);
            }
        } catch (Exception e) {
            // A missing/corrupt FULLTEXT index must not break chat. Fall back
            // to the in-memory token scan instead.
            chunks = List.of();
        }

        if (chunks.isEmpty()) {
            chunks = fallbackSearch(notebookId, selectedSourceIds, query, limit);
        }

        return chunks.stream().map(RetrievalService::toDocument).toList();
    }

    public List<Document> getDocumentsForNotebook(UUID notebookId, String query, int limit) {
        try {
            List<Document> results = search(notebookId, null, query, limit);

            List<Document> substantiveResults = results
                    .stream()
                    .filter(doc -> doc.getText() != null && doc.getText().length() > 100)
                    .toList();

            return substantiveResults.isEmpty() ?
                    results :
                    substantiveResults;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<SourceChunk> fallbackSearch(UUID notebookId, List<UUID> selectedSourceIds, String query, int limit) {
        Set<String> tokens = extractTokens(query);
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<SourceChunk> candidates = chunkRepository.findByNotebookIdOrderByChunkIndexAsc(
                notebookId,
                PageRequest.of(0, FALLBACK_SCAN_SIZE)
        );

        Set<UUID> selected = selectedSourceIds == null || selectedSourceIds.isEmpty() ?
                Set.of() :
                new HashSet<>(selectedSourceIds);
        List<ScoredChunk> scored = getScoredChunks(candidates, selected, tokens);

        return scored
                .stream()
                .sorted((a, b) -> Long.compare(b.hits(), a.hits()))
                .limit(limit)
                .map(ScoredChunk::chunk)
                .toList();
    }

    private static @NonNull List<ScoredChunk> getScoredChunks(
            List<SourceChunk> candidates,
            Set<UUID> selected,
            Set<String> tokens
    ) {
        List<ScoredChunk> scored = new ArrayList<>();
        for (SourceChunk chunk : candidates) {
            if (!selected.isEmpty() && !selected.contains(chunk.getSourceId())) {
                continue;
            }
            if (chunk.getContent() == null) {
                continue;
            }
            String lower = chunk.getContent().toLowerCase(Locale.ROOT);
            long hits = 0;
            for (String token : tokens) {
                if (lower.contains(token)) {
                    hits++;
                }
            }
            if (hits > 0) {
                scored.add(new ScoredChunk(chunk, hits));
            }
        }
        return scored;
    }

    private static Set<String> extractTokens(String query) {
        Set<String> tokens = new HashSet<>();
        for (String token : query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 3) {
                tokens.add(token);
            }
            if (tokens.size() >= 8) {
                break;
            }
        }
        return tokens;
    }

    private static Document toDocument(SourceChunk chunk) {
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

    private record ScoredChunk(
            SourceChunk chunk,
            long hits
    ) {
    }
}
