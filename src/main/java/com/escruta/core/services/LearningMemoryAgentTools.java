package com.escruta.core.services;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Per-request toolset exposed only during LEARNING mode when notebook learning memory is enabled.
 * Allows the tutor agent to record clear evidence of student mastery, misconceptions, or helpful
 * teaching observations without extra out-of-band LLM calls.
 */
public class LearningMemoryAgentTools {
    private final UUID notebookId;
    private final LearningMemoryService learningMemoryService;
    private final AgentTraceRecorder traceRecorder;

    public LearningMemoryAgentTools(
            UUID notebookId,
            LearningMemoryService learningMemoryService,
            AgentTraceRecorder traceRecorder
    ) {
        this.notebookId = notebookId;
        this.learningMemoryService = learningMemoryService;
        this.traceRecorder = traceRecorder;
    }

    @Tool(description = "Update the student's notebook learning memory when the conversation reveals clear evidence of concepts they have mastered, concepts they are struggling with, or a brief pedagogical observation about how they learn best. " + "Call this sparingly and ONLY when there is clear signal from the student's response—never for greetings or simple factual lookups.")
    public String updateLearningProgress(
            @ToolParam(description = "Optional short concept names (2-5 words each) the student clearly demonstrated understanding of")
            @Nullable List<String> masteredConcepts,
            @ToolParam(description = "Optional short concept names (2-5 words each) the student struggled with or had misconceptions about")
            @Nullable List<String> strugglingConcepts,
            @ToolParam(description = "Optional concise pedagogical observation (1 short sentence, e.g. 'Prefers step-by-step numerical examples before formulas')")
            @Nullable String observedNote,
            @ToolParam(description = "Brief explanation of what progress signal you observed")
            @Nullable String rationale
    ) {
        List<String> cleanMastered = LearningMemoryService.normalizeConceptList(masteredConcepts);
        List<String> cleanStruggling = LearningMemoryService.normalizeConceptList(strugglingConcepts);
        String cleanNote = observedNote != null && !observedNote.isBlank() ?
                observedNote.strip() :
                null;

        if (cleanMastered.isEmpty() && cleanStruggling.isEmpty() && cleanNote == null) {
            return "No learning progress changes were provided.";
        }

        List<String> summaryParts = new ArrayList<>();
        if (!cleanMastered.isEmpty()) {
            summaryParts.add("mastered: " + String.join(", ", cleanMastered));
        }
        if (!cleanStruggling.isEmpty()) {
            summaryParts.add("needs review: " + String.join(", ", cleanStruggling));
        }
        if (cleanNote != null && summaryParts.isEmpty()) {
            summaryParts.add("saved study note");
        }
        String detail = String.join(" · ", summaryParts);

        String stepId = traceRecorder.startStep(
                "memory",
                "Updating learning memory",
                detail,
                rationale != null && !rationale.isBlank() ?
                        rationale.trim() :
                        null
        );

        try {
            learningMemoryService.applyTutorObservation(notebookId, cleanMastered, cleanStruggling, cleanNote);
            int totalConcepts = cleanMastered.size() + cleanStruggling.size();
            String resultLabel = totalConcepts > 0 ?
                    "Saved " + totalConcepts + " concept" + (totalConcepts == 1 ?
                            "" :
                            "s") :
                    "Saved note";
            traceRecorder.completeStep(stepId, resultLabel, List.of());
            return "Learning memory updated (" + detail + ").";
        } catch (Exception e) {
            traceRecorder.failStep(stepId, "Could not update learning memory");
            return "Could not update learning memory: " + e.getMessage();
        }
    }
}
