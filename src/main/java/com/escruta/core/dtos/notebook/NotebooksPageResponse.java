package com.escruta.core.dtos.notebook;

import java.util.List;

public record NotebooksPageResponse(
        List<NotebookResponseDTO> notebooks,
        long total,
        boolean hasMore
) {
}
