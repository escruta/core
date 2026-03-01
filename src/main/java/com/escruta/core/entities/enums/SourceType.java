package com.escruta.core.entities.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SourceType {
    WEBSITE("Website"),
    YOUTUBE_VIDEO("YouTube Video"),
    FILE("File"),
    TEXT("Text");

    private final String value;

    SourceType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
