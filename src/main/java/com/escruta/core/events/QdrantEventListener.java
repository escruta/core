package com.escruta.core.events;

import com.escruta.core.services.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class QdrantEventListener {
    private final RetrievalService retrievalService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSourceDeleted(SourceDeletedEvent event) {
        try {
            retrievalService.deleteIndexedSource(event.getSourceId());
        } catch (Exception e) {
            log.error("Failed to delete indexed source {}: {}", event.getSourceId(), e.getMessage(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotebookDeleted(NotebookDeletedEvent event) {
        try {
            retrievalService.deleteIndexedNotebook(event.getNotebookId());
        } catch (Exception e) {
            log.error("Failed to delete indexed notebook {}: {}", event.getNotebookId(), e.getMessage(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserDeleted(UserDeletedEvent event) {
        for (UUID notebookId : event.getNotebookIds()) {
            try {
                retrievalService.deleteIndexedNotebook(notebookId);
            } catch (Exception e) {
                log.error("Failed to delete indexed notebook {}: {}", notebookId, e.getMessage(), e);
            }
        }
    }
}
