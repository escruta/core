package com.escruta.core.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class SourceDeletedEvent extends ApplicationEvent {
    private final UUID sourceId;

    public SourceDeletedEvent(Object source, UUID sourceId) {
        super(source);
        this.sourceId = sourceId;
    }
}
