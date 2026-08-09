package com.escruta.core.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SearchRequest(
        @NotBlank
        String query,
        @Min(1)
        @Max(50)
        int maxResults,
        @NotNull
        UUID notebookId
) {
    public SearchRequest {
        if (query != null)
            query = query.strip();
    }
}
