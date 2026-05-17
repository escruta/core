package com.escruta.core.dtos.folder;

import jakarta.validation.constraints.NotBlank;

public record FolderCreationDTO(
        @NotBlank String title,
        String color
) {
}
