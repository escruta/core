package com.escruta.core.services;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AsyncVectorIndexingService {
    private final RetrievalService retrievalService;

    @Async
    public void indexSourceInVectorStore(UUID notebookId, UUID sourceId, String title, String link, String content) {
        try {
            TokenTextSplitter textSplitter = new TokenTextSplitter(500, 100, 5, 10000, true);
            List<Document> chunks = textSplitter.apply(List.of(new Document(content)));

            for (int i = 0; i < chunks.size(); i++) {
                try {
                    retrievalService.indexSourceChunk(notebookId, sourceId, title, link, chunks.get(i), i);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }
}
