package com.escruta.core.jobs;

import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.SourceRepository;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.ScrollResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QdrantReconciliationJob {
    @Autowired(required = false)
    private QdrantClient qdrantClient;
    private final SourceRepository sourceRepository;
    private final NotebookRepository notebookRepository;

    @Value("${spring.ai.vectorstore.qdrant.collection-name:escruta}")
    private String collectionName;

    @Scheduled(cron = "0 0 3 * * SUN")
    @Transactional(readOnly = true)
    public void reconcile() {
        if (qdrantClient == null) return;
        try {
            PointId nextOffset = null;
            boolean hasMore = true;

            while (hasMore) {
                ScrollPoints.Builder requestBuilder = ScrollPoints
                        .newBuilder()
                        .setCollectionName(collectionName)
                        .setLimit(500)
                        .setWithPayload(io.qdrant.client.grpc.Points.WithPayloadSelector
                                .newBuilder()
                                .setEnable(true)
                                .build());

                if (nextOffset != null) {
                    requestBuilder.setOffset(nextOffset);
                }

                ScrollResponse response = qdrantClient.scrollAsync(requestBuilder.build()).get();
                List<RetrievedPoint> points = response.getResultList();
                List<PointId> pointsToDelete = new ArrayList<>();

                for (RetrievedPoint point : points) {
                    Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload = point.getPayloadMap();

                    boolean shouldDelete = false;

                    if (payload.containsKey("sourceId")) {
                        String sourceIdStr = payload.get("sourceId").getStringValue();
                        try {
                            UUID sourceId = UUID.fromString(sourceIdStr);
                            if (!sourceRepository.existsById(sourceId)) {
                                shouldDelete = true;
                            }
                        } catch (IllegalArgumentException e) {
                            shouldDelete = true;
                        }
                    }

                    if (!shouldDelete && payload.containsKey("notebookId")) {
                        String notebookIdStr = payload.get("notebookId").getStringValue();
                        try {
                            UUID notebookId = UUID.fromString(notebookIdStr);
                            if (!notebookRepository.existsById(notebookId)) {
                                shouldDelete = true;
                            }
                        } catch (IllegalArgumentException e) {
                            shouldDelete = true; // Invalid UUID format
                        }
                    }

                    if (shouldDelete) {
                        pointsToDelete.add(point.getId());
                    }
                }

                if (!pointsToDelete.isEmpty()) {
                    qdrantClient.deleteAsync(collectionName, pointsToDelete).get();
                }

                if (response.hasNextPageOffset()) {
                    nextOffset = response.getNextPageOffset();
                } else {
                    hasMore = false;
                }
            }
        } catch (Exception ignored) {
        }
    }
}
