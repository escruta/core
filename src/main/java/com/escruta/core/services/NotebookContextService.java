package com.escruta.core.services;

import com.escruta.core.configs.CacheConfiguration;
import com.escruta.core.repositories.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotebookContextService {
    static final String DEFAULT_QUERY = "core concepts key ideas summary main topic definitions overview";
    private static final int MAX_CONTEXT_CHARS = 60_000;

    private final SourceRepository sourceRepository;
    private final RetrievalService retrievalService;

    @Cacheable(cacheNames = CacheConfiguration.NOTEBOOK_CONTEXT, key = "#notebookId.toString() + ':' + #limit")
    public List<Document> getDocuments(UUID notebookId, int limit) {
        if (!sourceRepository.existsByNotebookId(notebookId)) {
            return List.of();
        }
        return retrievalService.getDocumentsForNotebook(notebookId, DEFAULT_QUERY, limit);
    }

    @Cacheable(cacheNames = CacheConfiguration.NOTEBOOK_CONTEXT, key = "#notebookId.toString() + ':text:' + #limit")
    public String getContext(UUID notebookId, int limit) {
        String context = getDocuments(notebookId, limit)
                .stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n\n"));
        return context.length() > MAX_CONTEXT_CHARS ?
                context.substring(0, MAX_CONTEXT_CHARS) :
                context;
    }

    @CacheEvict(cacheNames = {CacheConfiguration.NOTEBOOK_CONTEXT, CacheConfiguration.EXAMPLE_QUESTIONS}, allEntries = true)
    public void evict() {
        // Cache eviction only; the notebook id is part of the key.
    }
}
