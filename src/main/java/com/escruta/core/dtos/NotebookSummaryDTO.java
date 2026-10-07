package com.escruta.core.dtos;

import java.util.List;

public record NotebookSummaryDTO(
        String summary,
        List<String> topics
) {
}
