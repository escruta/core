package com.escruta.core.dtos.tools;

import java.util.List;

public record FlashcardsResponse(
        List<Flashcard> flashcards
) {
    public record Flashcard(
            String front,
            String back
    ) {
    }
}
