package com.escruta.core.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class NotebookDeletedEvent extends ApplicationEvent {
    private final UUID notebookId;

    public NotebookDeletedEvent(Object source, UUID notebookId) {
        super(source);
        this.notebookId = notebookId;
    }
}
