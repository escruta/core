package com.escruta.core.dtos.tools;

import java.util.List;

public record QuestionnaireResponse(
        String title,
        List<Question> questions
) {
    public record Question(
            String type,
            String question,
            List<String> options,
            Integer correctAnswerIndex,
            Boolean correctAnswerBoolean,
            String sampleAnswer,
            String explanation
    ) {
    }
}
