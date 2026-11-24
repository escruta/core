package com.escruta.core.services;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.UUID;

/**
 * Read-only per-request toolset exposed when notebook learning memory is enabled.
 * Allows the agent (in both NORMAL and LEARNING modes) to inspect what the user
 * already knows, struggles with, and wants to achieve on demand without bloating
 * the system prompt.
 */
public class LearningMemoryReadTools {
    private final UUID notebookId;
    private final LearningMemoryService learningMemoryService;
    private final AgentTraceRecorder traceRecorder;

    public LearningMemoryReadTools(
            UUID notebookId,
            LearningMemoryService learningMemoryService,
            AgentTraceRecorder traceRecorder
    ) {
        this.notebookId = notebookId;
        this.learningMemoryService = learningMemoryService;
        this.traceRecorder = traceRecorder;
    }

    @Tool(description = "Inspect the user's learning memory for this notebook: proficiency level, learning goal, concepts already mastered, concepts needing review, tutoring notes, and practice stats. " + "Call this when tailoring explanations, checking what the user already knows about a topic, or answering questions about what to study or review next.")
    public String getLearningMemory(
            @ToolParam(description = "Optional topic or concept keyword to prioritize matching concepts; omit to load the full notebook learning profile")
            @Nullable String topic,
            @ToolParam(description = "Brief explanation of why you are checking the learning memory")
            @Nullable String rationale
    ) {
        String cleanTopic = topic != null && !topic.isBlank() ?
                topic.trim() :
                null;
        String cleanRationale = rationale != null && !rationale.isBlank() ?
                rationale.trim() :
                null;

        String stepId = traceRecorder.startStep("memory", "Checking learning memory", cleanTopic, cleanRationale);

        try {
            LearningMemoryService.MemoryToolQueryResult result = learningMemoryService.queryMemoryForTool(
                    notebookId,
                    cleanTopic
            );
            traceRecorder.completeStep(stepId, result.stepSummary(), List.of());
            return result.content();
        } catch (Exception e) {
            traceRecorder.failStep(stepId, "Could not load learning memory");
            return "Could not load learning memory: " + e.getMessage();
        }
    }
}
