package com.escruta.core.dtos.notegroup;

import jakarta.validation.constraints.NotBlank;

public record NoteGroupCreationDTO(
        @NotBlank
        String title,
        String color
) {
}
