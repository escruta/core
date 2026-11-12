package com.escruta.core.dtos.notegroup;

import jakarta.validation.constraints.NotBlank;

public record NoteGroupUpdateDTO(
        @NotBlank
        String title,
        String color
) {
}
