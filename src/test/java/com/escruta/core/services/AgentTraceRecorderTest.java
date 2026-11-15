package com.escruta.core.services;

import com.escruta.core.dtos.AgentStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentTraceRecorder Tests")
class AgentTraceRecorderTest {
    @Test
    @DisplayName("Should record interleaved thoughts, tool steps, and text segments in chronological order")
    void recordsInterleavedBlocksInOrder() {
        List<AgentStep> emittedSteps = new ArrayList<>();
        List<String> thoughtDeltas = new ArrayList<>();
        AgentTraceRecorder recorder = new AgentTraceRecorder(
                emittedSteps::add,
                (stepId, delta) -> thoughtDeltas.add(stepId + ":" + delta)
        );

        recorder.appendThought("Let me ");
        recorder.appendThought("search for X.");

        String searchId = recorder.startStep("search", "Searching sources", "X", "Need definition of X");
        recorder.completeStep(searchId, "Found 3 passages", List.of("Source A"));

        recorder.appendAnswer("First part of the answer.");
        recorder.appendThought("Wait, let me also verify Y.");
        recorder.appendAnswer("Second part of the answer.");
        recorder.complete();

        List<AgentStep> snapshot = recorder.getStepsSnapshot();
        assertThat(snapshot)
                .extracting(AgentStep::type)
                .containsExactly("thought", "search", "text", "thought", "text");
        assertThat(snapshot).extracting(AgentStep::status).containsOnly("done");
        assertThat(snapshot.get(0).detail()).isEqualTo("Let me search for X.");
        assertThat(snapshot.get(2).detail()).isEqualTo("First part of the answer.");
        assertThat(snapshot.get(3).detail()).isEqualTo("Wait, let me also verify Y.");
        assertThat(snapshot.get(4).detail()).isEqualTo("Second part of the answer.");
        assertThat(thoughtDeltas).hasSize(3);
        assertThat(emittedSteps).isNotEmpty();
    }

    @Test
    @DisplayName("Should deduplicate cumulative and repeated reasoning chunks within the same turn")
    void deduplicatesCumulativeAndRepeatedReasoning() {
        AgentTraceRecorder recorder = new AgentTraceRecorder(null, null);

        recorder.appendThought("Let");
        recorder.appendThought("Let me search the sources.");
        recorder.appendThought("Let me search the sources.");
        recorder.appendAnswer("Hello");
        recorder.appendThought("Let me search the sources.");
        recorder.appendAnswer(" world.");
        recorder.complete();

        List<AgentStep> snapshot = recorder.getStepsSnapshot();
        assertThat(snapshot).extracting(AgentStep::type).containsExactly("thought", "text");
        assertThat(snapshot.get(0).detail()).isEqualTo("Let me search the sources.");
        assertThat(snapshot.get(1).detail()).isEqualTo("Hello world.");
    }
}
