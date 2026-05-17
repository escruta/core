package com.escruta.core.dtos.folder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.util.UUID;

public record FolderResponseDTO(
        @NotNull UUID id,
        @NotNull UUID userId,
        @NotBlank String title,
        String color,
        Timestamp createdAt,
        Timestamp updatedAt
) {
}
