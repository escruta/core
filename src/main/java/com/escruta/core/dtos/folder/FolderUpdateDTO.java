package com.escruta.core.dtos.folder;

import jakarta.validation.constraints.NotBlank;

public record FolderUpdateDTO(
        @NotBlank String title,
        String color
) {
}
