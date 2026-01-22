package com.escruta.core.services;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetrievalService {
    private final VectorStore vectorStore;

    public QuestionAnswerAdvisor getQuestionAnswerAdvisor(UUID notebookId) {
        return QuestionAnswerAdvisor
                .builder(vectorStore)
                .searchRequest(SearchRequest
                        .builder()
                        .topK(5)
                        .similarityThreshold(0.0)
                        .filterExpression(new Filter.Expression(
                                Filter.ExpressionType.EQ,
                                new Filter.Key("notebookId"),
                                new Filter.Value(notebookId.toString())
                        ))
                        .build())
                .build();
    }

    public void deleteIndexedSource(UUID sourceId) {
        try {
            vectorStore.delete(new Filter.Expression(
                    Filter.ExpressionType.EQ,
                    new Filter.Key("sourceId"),
                    new Filter.Value(sourceId.toString())
            ));
        } catch (Exception ignored) {
        }
    }

    public List<Document> getDocumentsForNotebook(UUID notebookId, int limit) {
        try {
            SearchRequest searchRequest = SearchRequest
                    .builder()
                    .query("key concepts definitions explanations important information details")
                    .topK(limit)
                    .similarityThreshold(0.0)
                    .filterExpression(new Filter.Expression(
                            Filter.ExpressionType.EQ,
                            new Filter.Key("notebookId"),
                            new Filter.Value(notebookId.toString())
                    ))
                    .build();
            List<Document> results = vectorStore.similaritySearch(searchRequest);

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

    public void indexSourceChunk(
            UUID notebookId,
            UUID sourceId,
            String title,
            String link,
            Document chunk,
            int chunkIndex
    ) {
        try {
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
                    String.valueOf(chunkIndex)
            )
            );
            vectorStore.add(List.of(document));
        } catch (Exception ignored) {
        }
    }
}
