package com.escruta.core.dtos.notebook;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record NotebookCreationDTO(
        String icon,
        @NotBlank
        String title,
        UUID folderId
) {
}
