package com.escruta.core.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;
import java.util.UUID;

@Getter
public class UserDeletedEvent extends ApplicationEvent {
    private final List<UUID> notebookIds;

    public UserDeletedEvent(Object source, List<UUID> notebookIds) {
        super(source);
        this.notebookIds = notebookIds;
    }
}
