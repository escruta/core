package com.escruta.core.dtos.tools;

import java.util.List;

public record StudyGuideResponse(
        String overview,
        List<KeyConcept> keyConcepts,
        List<String> importantDetails,
        List<String> connections,
        List<String> reviewQuestions
) {
    public record KeyConcept(
            String term,
            String definition
    ) {
    }
}
