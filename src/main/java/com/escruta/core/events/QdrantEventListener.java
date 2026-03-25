package com.escruta.core.events;

import com.escruta.core.services.RetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QdrantEventListener {
    private final RetrievalService retrievalService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSourceDeleted(SourceDeletedEvent event) {
        retrievalService.deleteIndexedSource(event.getSourceId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotebookDeleted(NotebookDeletedEvent event) {
        retrievalService.deleteIndexedNotebook(event.getNotebookId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserDeleted(UserDeletedEvent event) {
        for (UUID notebookId : event.getNotebookIds()) {
            retrievalService.deleteIndexedNotebook(notebookId);
        }
    }
}
