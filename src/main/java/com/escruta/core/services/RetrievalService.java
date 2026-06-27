package com.escruta.core.services;

import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetrievalService {
    @Autowired(required = false)
    private VectorStore vectorStore;

    public CustomQuestionAnswerAdvisor getQuestionAnswerAdvisor(UUID notebookId, List<UUID> selectedSourceIds) {
        if (vectorStore == null) {
            return null;
        }
        Filter.Expression notebookFilter = new Filter.Expression(
                Filter.ExpressionType.EQ,
                new Filter.Key("notebookId"),
                new Filter.Value(notebookId.toString())
        );

        Filter.Expression finalFilter = notebookFilter;

        if (selectedSourceIds != null && !selectedSourceIds.isEmpty()) {
            List<String> sourceIdStrings = selectedSourceIds.stream().map(UUID::toString).toList();

            Filter.Expression sourceFilter = new Filter.Expression(
                    Filter.ExpressionType.IN,
                    new Filter.Key("sourceId"),
                    new Filter.Value(sourceIdStrings)
            );

            finalFilter = new Filter.Expression(Filter.ExpressionType.AND, notebookFilter, sourceFilter);
        }

        return CustomQuestionAnswerAdvisor
                .builder(vectorStore)
                .searchRequest(SearchRequest
                        .builder()
                        .topK(5)
                        .similarityThreshold(0.0)
                        .filterExpression(finalFilter)
                        .build())
                .build();
    }

    @Retryable(backoff = @Backoff(delay = 2000))
    public void deleteIndexedSource(UUID sourceId) {
        if (vectorStore == null)
            return;
        vectorStore.delete(new Filter.Expression(
                Filter.ExpressionType.EQ,
                new Filter.Key("sourceId"),
                new Filter.Value(sourceId.toString())
        ));
    }

    @Retryable(backoff = @Backoff(delay = 2000))
    public void deleteIndexedNotebook(UUID notebookId) {
        if (vectorStore == null)
            return;
        vectorStore.delete(new Filter.Expression(
                Filter.ExpressionType.EQ,
                new Filter.Key("notebookId"),
                new Filter.Value(notebookId.toString())
        ));
    }

    public List<Document> getDocumentsForNotebook(UUID notebookId, String query, int limit) {
        if (vectorStore == null)
            return List.of();
        try {
            SearchRequest searchRequest = SearchRequest
                    .builder()
                    .query(query)
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

    public void indexSourceChunks(List<Document> chunks) {
        if (vectorStore == null)
            return;
        try {
            vectorStore.add(chunks);
        } catch (Exception ignored) {
        }
    }
}
