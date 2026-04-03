package com.escruta.core.services;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AsyncVectorIndexingService {
    private final RetrievalService retrievalService;

    public void indexSourceInVectorStore(UUID notebookId, UUID sourceId, String title, String link, String content) {
        try {
            TokenTextSplitter textSplitter = new TokenTextSplitter(500, 100, 5, 10000, true);
            List<Document> chunks = textSplitter.apply(List.of(new Document(content)));

            List<Document> documentsToSave = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                try {
                    Document chunk = chunks.get(i);
                    String text = chunk.getText() != null ?
                            chunk.getText() :
                            "";
                    Document document = new Document(
                            UUID.randomUUID().toString(), text, Map.of(
                            "sourceId",
                            sourceId.toString(),
                            "notebookId",
                            notebookId.toString(),
                            "title",
                            title != null ?
                                    title :
                                    "Untitled",
                            "link",
                            link != null ?
                                    link :
                                    "",
                            "chunkIndex",
                            String.valueOf(i)
                    )
                    );
                    documentsToSave.add(document);
                } catch (Exception ignored) {
                }
            }
            if (!documentsToSave.isEmpty()) {
                retrievalService.indexSourceChunks(documentsToSave);
            }
        } catch (Exception ignored) {
        }
    }
}
