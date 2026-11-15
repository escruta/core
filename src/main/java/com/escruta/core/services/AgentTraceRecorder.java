package com.escruta.core.services;

import com.escruta.core.dtos.AgentStep;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Thread-safe per-turn recorder for chronological agent execution steps,
 * streamed model thoughts, and generated text segments.
 */
public class AgentTraceRecorder {
    private final long turnStartNanos = System.nanoTime();
    private final Map<String, AgentStep> steps = new LinkedHashMap<>();
    private final Map<String, Long> stepStartNanos = new HashMap<>();
    private final @Nullable Consumer<AgentStep> stepListener;
    private final @Nullable BiConsumer<String, String> thoughtListener;

    private int stepCounter = 0;
    private @Nullable String activeThoughtStepId;
    private @Nullable String activeTextStepId;
    private final StringBuilder activeThoughtText = new StringBuilder();
    private final StringBuilder activeTextBuffer = new StringBuilder();
    private String lastTurnReasoning = "";

    public AgentTraceRecorder(
            @Nullable Consumer<AgentStep> stepListener,
            @Nullable BiConsumer<String, String> thoughtListener
    ) {
        this.stepListener = stepListener;
        this.thoughtListener = thoughtListener;
    }

    public synchronized String startStep(
            String type,
            String title,
            @Nullable String detail,
            @Nullable String rationale
    ) {
        closeActiveThoughtLocked();
        closeActiveTextLocked();
        lastTurnReasoning = "";

        String id = "step-" + (++stepCounter);
        AgentStep step = AgentStep.running(id, type, title, detail, rationale);
        steps.put(id, step);
        stepStartNanos.put(id, System.nanoTime());
        emitStep(step);
        return id;
    }

    public synchronized void completeStep(String stepId, @Nullable String result, @Nullable List<String> sources) {
        AgentStep existing = steps.get(stepId);
        if (existing == null) {
            return;
        }
        AgentStep updated = existing.complete(result, sources, elapsedMs(stepId));
        steps.put(stepId, updated);
        emitStep(updated);
    }

    public synchronized void completeStepWithDetail(
            String stepId,
            @Nullable String detail,
            @Nullable String result,
            @Nullable List<String> sources
    ) {
        AgentStep existing = steps.get(stepId);
        if (existing == null) {
            return;
        }
        AgentStep updated = existing.withDetail(detail).complete(result, sources, elapsedMs(stepId));
        steps.put(stepId, updated);
        emitStep(updated);
    }

    public synchronized void failStep(String stepId, @Nullable String errorMessage) {
        AgentStep existing = steps.get(stepId);
        if (existing == null) {
            return;
        }
        AgentStep updated = existing.fail(errorMessage, elapsedMs(stepId));
        steps.put(stepId, updated);
        emitStep(updated);
    }

    public synchronized void appendThought(@Nullable String incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }

        String delta = extractNewThoughtDeltaLocked(incoming);
        if (delta.isEmpty()) {
            return;
        }

        closeActiveTextLocked();

        if (activeThoughtStepId == null) {
            if (delta.isBlank()) {
                return;
            }
            String id = "step-" + (++stepCounter);
            activeThoughtStepId = id;
            activeThoughtText.setLength(0);
            AgentStep step = AgentStep.running(id, "thought", "Thought", "", null);
            steps.put(id, step);
            stepStartNanos.put(id, System.nanoTime());
            emitStep(step);
        }

        activeThoughtText.append(delta);
        AgentStep current = steps.get(activeThoughtStepId);
        if (current != null) {
            steps.put(activeThoughtStepId, current.withDetail(activeThoughtText.toString()));
        }
        if (thoughtListener != null) {
            thoughtListener.accept(activeThoughtStepId, delta);
        }
    }

    public synchronized void appendAnswer(@Nullable String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        closeActiveThoughtLocked();

        if (activeTextStepId == null) {
            if (delta.isBlank()) {
                return;
            }
            String id = "step-" + (++stepCounter);
            activeTextStepId = id;
            activeTextBuffer.setLength(0);
            AgentStep step = AgentStep.running(id, "text", "", "", null);
            steps.put(id, step);
            stepStartNanos.put(id, System.nanoTime());
        }

        activeTextBuffer.append(delta);
        AgentStep current = steps.get(activeTextStepId);
        if (current != null) {
            steps.put(activeTextStepId, current.withDetail(activeTextBuffer.toString()));
        }
    }

    public synchronized void complete() {
        closeActiveThoughtLocked();
        closeActiveTextLocked();
        for (Map.Entry<String, AgentStep> entry : new ArrayList<>(steps.entrySet())) {
            AgentStep step = entry.getValue();
            if ("running".equals(step.status())) {
                AgentStep updated = step.complete(step.result(), step.sources(), elapsedMs(step.id()));
                steps.put(entry.getKey(), updated);
                if (!"text".equals(step.type())) {
                    emitStep(updated);
                }
            }
        }
    }

    public synchronized List<AgentStep> getStepsSnapshot() {
        List<AgentStep> result = new ArrayList<>(steps.size());
        for (AgentStep step : steps.values()) {
            if (("thought".equals(step.type()) || "text".equals(step.type())) && (step.detail() == null || step
                    .detail()
                    .isBlank())) {
                continue;
            }
            result.add(step);
        }
        return List.copyOf(result);
    }

    public synchronized long getTotalDurationMs() {
        return Math.max(0L, (System.nanoTime() - turnStartNanos) / 1_000_000L);
    }

    private String extractNewThoughtDeltaLocked(String incoming) {
        if (lastTurnReasoning.isEmpty()) {
            lastTurnReasoning = incoming;
            return incoming;
        }
        if (incoming.equals(lastTurnReasoning)) {
            return "";
        }
        String trimmedIncoming = incoming.trim();
        String trimmedLast = lastTurnReasoning.trim();
        if (!trimmedLast.isEmpty() && trimmedIncoming.equals(trimmedLast)) {
            return "";
        }
        if (incoming.length() > lastTurnReasoning.length() && incoming.startsWith(lastTurnReasoning)) {
            String suffix = incoming.substring(lastTurnReasoning.length());
            lastTurnReasoning = incoming;
            return suffix;
        }
        if (lastTurnReasoning.startsWith(incoming) && incoming.length() >= 8) {
            return "";
        }
        lastTurnReasoning = lastTurnReasoning + incoming;
        return incoming;
    }

    private void closeActiveThoughtLocked() {
        if (activeThoughtStepId == null) {
            return;
        }
        String thoughtId = activeThoughtStepId;
        activeThoughtStepId = null;
        AgentStep existing = steps.get(thoughtId);
        if (existing != null) {
            String trimmed = activeThoughtText.toString().strip();
            if (trimmed.isEmpty()) {
                steps.remove(thoughtId);
                return;
            }
            AgentStep updated = AgentStep.done(
                    thoughtId,
                    existing.type(),
                    existing.title(),
                    trimmed,
                    existing.rationale(),
                    null,
                    null,
                    elapsedMs(thoughtId)
            );
            steps.put(thoughtId, updated);
            emitStep(updated);
        }
        activeThoughtText.setLength(0);
    }

    private void closeActiveTextLocked() {
        if (activeTextStepId == null) {
            return;
        }
        String textId = activeTextStepId;
        activeTextStepId = null;
        AgentStep existing = steps.get(textId);
        if (existing != null) {
            String text = activeTextBuffer.toString();
            if (text.isBlank()) {
                steps.remove(textId);
            } else {
                steps.put(textId, AgentStep.done(textId, "text", "", text, null, null, null, elapsedMs(textId)));
            }
        }
        activeTextBuffer.setLength(0);
    }

    private long elapsedMs(String stepId) {
        Long start = stepStartNanos.get(stepId);
        if (start == null) {
            return 0L;
        }
        return Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
    }

    private void emitStep(AgentStep step) {
        if (stepListener != null) {
            stepListener.accept(step);
        }
    }
}
