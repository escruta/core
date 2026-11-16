package com.escruta.core.services;

import com.escruta.core.dtos.AgentStep;
import com.escruta.core.dtos.source.SourceResponseDTO;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.Source;
import com.escruta.core.entities.SourceChunk;
import com.escruta.core.entities.SourceGroup;
import com.escruta.core.entities.enums.SourceStatus;
import com.escruta.core.entities.enums.SourceType;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.SourceChunkRepository;
import com.escruta.core.repositories.SourceGroupRepository;
import com.escruta.core.repositories.SourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.data.domain.PageRequest;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotebookAgentTools Tests")
class NotebookAgentToolsTest {

    @Mock
    private RetrievalService retrievalService;
    @Mock
    private SourceRepository sourceRepository;
    @Mock
    private SourceChunkRepository chunkRepository;
    @Mock
    private NotebookRepository notebookRepository;
    @Mock
    private NotebookContextService notebookContextService;
    @Mock
    private SourceGroupRepository sourceGroupRepository;

    private NotebookAgentTools createTools(UUID notebookId, List<UUID> selectedSourceIds, AgentTraceRecorder recorder) {
        return new NotebookAgentTools(
                notebookId,
                selectedSourceIds,
                retrievalService,
                sourceRepository,
                chunkRepository,
                notebookRepository,
                notebookContextService,
                sourceGroupRepository,
                recorder
        );
    }

    private static SourceChunk chunk(UUID notebookId, UUID sourceId, int index, String title, String content) {
        SourceChunk chunk = new SourceChunk();
        chunk.setId(UUID.randomUUID());
        chunk.setNotebookId(notebookId);
        chunk.setSourceId(sourceId);
        chunk.setChunkIndex(index);
        chunk.setTitle(title);
        chunk.setContent(content);
        return chunk;
    }

    @Test
    @DisplayName("searchSources should collect deduplicated documents and record search step")
    void searchSourcesCollectsDocumentsAndRecordsStep() {
        UUID notebookId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        AgentTraceRecorder recorder = new AgentTraceRecorder(null, null);

        Document doc = new Document(
                chunkId.toString(),
                "Bell's theorem shows local hidden variables cannot reproduce quantum predictions.",
                Map.of("sourceId", sourceId.toString(), "title", "Quantum Lecture", "chunkIndex", "2")
        );
        when(retrievalService.search(notebookId, List.of(sourceId), "Bell theorem", 6)).thenReturn(List.of(doc));

        NotebookAgentTools tools = createTools(notebookId, List.of(sourceId), recorder);

        String response = tools.searchSources("Bell theorem", "Find definition of Bell theorem");

        assertThat(response)
                .contains("Source ID: " + chunkId)
                .contains("Document ID: " + sourceId)
                .contains("Chunk: 2")
                .contains("Quantum Lecture");
        assertThat(tools.getCollectedDocuments()).hasSize(1);
        List<AgentStep> steps = recorder.getStepsSnapshot();
        assertThat(steps).hasSize(1);
        assertThat(steps.getFirst().type()).isEqualTo("search");
        assertThat(steps.getFirst().status()).isEqualTo("done");
        assertThat(steps.getFirst().sources()).containsExactly("Quantum Lecture");
    }

    @Test
    @DisplayName("searchExactMatch should find literal phrase in scoped sources and record search step")
    void searchExactMatchFindsLiteralPhrase() {
        UUID notebookId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        AgentTraceRecorder recorder = new AgentTraceRecorder(null, null);

        SourceChunk match = chunk(
                notebookId,
                sourceId,
                4,
                "RFC 9110",
                "The status code 429 Too Many Requests indicates rate limiting."
        );
        when(chunkRepository.findExactByNotebookAndSources(
                notebookId,
                List.of(sourceId),
                "429 Too Many Requests",
                PageRequest.of(0, 8)
        )).thenReturn(List.of(match));

        NotebookAgentTools tools = createTools(notebookId, List.of(sourceId), recorder);

        String response = tools.searchExactMatch("429 Too Many Requests", null, "Locate exact status code");

        assertThat(response)
                .contains("Source ID: " + match.getId())
                .contains("429 Too Many Requests")
                .contains("RFC 9110");
        assertThat(tools.getCollectedDocuments()).hasSize(1);
        AgentStep step = recorder.getStepsSnapshot().getFirst();
        assertThat(step.type()).isEqualTo("search");
        assertThat(step.status()).isEqualTo("done");
        assertThat(step.sources()).containsExactly("RFC 9110");
    }

    @Test
    @DisplayName("getSourceOverview should load summary, opening chunks, and record human-readable title")
    void getSourceOverviewLoadsSummaryAndOpeningChunks() {
        UUID notebookId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        AgentTraceRecorder recorder = new AgentTraceRecorder(null, null);

        Source source = new Source();
        source.setId(sourceId);
        source.setTitle("Paper on Entanglement");
        source.setType(SourceType.FILE);
        source.setSummary("Overview of Bell inequalities.");

        SourceChunk opening = new SourceChunk();
        opening.setId(chunkId);
        opening.setSourceId(sourceId);
        opening.setNotebookId(notebookId);
        opening.setChunkIndex(0);
        opening.setTitle("Paper on Entanglement");
        opening.setContent("Opening paragraph about entanglement.");

        when(sourceRepository.findByIdAndNotebookId(sourceId, notebookId)).thenReturn(Optional.of(source));
        when(chunkRepository.countByNotebookIdAndSourceId(notebookId, sourceId)).thenReturn(12L);
        when(chunkRepository.findByNotebookIdAndSourceIdInOrderByChunkIndexAsc(
                notebookId,
                List.of(sourceId),
                PageRequest.of(0, 3)
        )).thenReturn(List.of(opening));

        NotebookAgentTools tools = createTools(notebookId, List.of(sourceId), recorder);

        String result = tools.getSourceOverview(sourceId.toString(), "Understand the paper structure");

        assertThat(result)
                .contains("Overview of Bell inequalities.")
                .contains("Source ID: " + chunkId)
                .contains("Total chunks: 12");
        assertThat(tools.getCollectedDocuments()).hasSize(1);
        AgentStep step = recorder.getStepsSnapshot().getFirst();
        assertThat(step.type()).isEqualTo("read");
        assertThat(step.detail()).isEqualTo("Paper on Entanglement");
        assertThat(step.status()).isEqualTo("done");
    }

    @Test
    @DisplayName("readSourceChunks should return requested sequential chunks and collect them for citations")
    void readSourceChunksReturnsSequentialRange() {
        UUID notebookId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        AgentTraceRecorder recorder = new AgentTraceRecorder(null, null);

        Source source = new Source();
        source.setId(sourceId);
        source.setTitle("Long Report");
        source.setType(SourceType.FILE);

        SourceChunk c3 = chunk(notebookId, sourceId, 3, "Long Report", "Section 2 begins here.");
        SourceChunk c4 = chunk(notebookId, sourceId, 4, "Long Report", "Section 2 continues with data.");

        when(sourceRepository.findByIdAndNotebookId(sourceId, notebookId)).thenReturn(Optional.of(source));
        when(chunkRepository.countByNotebookIdAndSourceId(notebookId, sourceId)).thenReturn(10L);
        when(chunkRepository.findByNotebookIdAndSourceIdAndChunkIndexBetweenOrderByChunkIndexAsc(
                notebookId,
                sourceId,
                3,
                4
        )).thenReturn(List.of(c3, c4));

        NotebookAgentTools tools = createTools(notebookId, List.of(sourceId), recorder);

        String result = tools.readSourceChunks(sourceId.toString(), 3, 2, "Read section 2");

        assertThat(result)
                .contains("chunks 3-4 (total chunks: 10)")
                .contains("Source ID: " + c3.getId())
                .contains("Source ID: " + c4.getId())
                .contains("Section 2 begins here.");
        assertThat(tools.getCollectedDocuments()).hasSize(2);
        AgentStep step = recorder.getStepsSnapshot().getFirst();
        assertThat(step.type()).isEqualTo("read");
        assertThat(step.detail()).isEqualTo("Long Report (chunks 3-4)");
        assertThat(step.status()).isEqualTo("done");
    }

    @Test
    @DisplayName("getSurroundingChunks should load adjacent passages around anchor passage")
    void getSurroundingChunksLoadsContextWindow() {
        UUID notebookId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        AgentTraceRecorder recorder = new AgentTraceRecorder(null, null);

        SourceChunk c4 = chunk(notebookId, sourceId, 4, "Quantum Notes", "Before the theorem.");
        SourceChunk c5 = chunk(notebookId, sourceId, 5, "Quantum Notes", "Anchor passage.");
        SourceChunk c6 = chunk(notebookId, sourceId, 6, "Quantum Notes", "After the theorem.");

        when(chunkRepository.findByIdAndNotebookId(c5.getId(), notebookId)).thenReturn(Optional.of(c5));
        when(chunkRepository.findByNotebookIdAndSourceIdAndChunkIndexBetweenOrderByChunkIndexAsc(
                notebookId,
                sourceId,
                4,
                6
        )).thenReturn(List.of(c4, c5, c6));

        NotebookAgentTools tools = createTools(notebookId, List.of(sourceId), recorder);

        String result = tools.getSurroundingChunks(c5.getId().toString(), 1, "Recover full proof");

        assertThat(result)
                .contains("Chunks 4-6 (anchor chunk: 5)")
                .contains("[ANCHOR PASSAGE]")
                .contains("Before the theorem.")
                .contains("After the theorem.");
        assertThat(tools.getCollectedDocuments()).hasSize(3);
        AgentStep step = recorder.getStepsSnapshot().getFirst();
        assertThat(step.type()).isEqualTo("read");
        assertThat(step.status()).isEqualTo("done");
    }

    @Test
    @DisplayName("listSources should filter by group and query within scoped ready sources")
    void listSourcesFiltersByGroupAndQuery() {
        UUID notebookId = UUID.randomUUID();
        UUID groupA = UUID.randomUUID();
        UUID groupB = UUID.randomUUID();
        UUID source1 = UUID.randomUUID();
        UUID source2 = UUID.randomUUID();
        UUID source3 = UUID.randomUUID();
        AgentTraceRecorder recorder = new AgentTraceRecorder(null, null);

        SourceGroup sgA = new SourceGroup();
        sgA.setId(groupA);
        sgA.setTitle("Primary Papers");

        Timestamp now = Timestamp.from(Instant.now());
        when(sourceGroupRepository.findByNotebookIdOrderByCreatedAtDesc(notebookId)).thenReturn(List.of(sgA));
        when(sourceRepository.findSummariesByNotebookId(notebookId)).thenReturn(List.of(
                new SourceResponseDTO(
                        source1,
                        notebookId,
                        groupA,
                        null,
                        "Bell Inequality Paper",
                        false,
                        null,
                        SourceStatus.READY,
                        SourceType.FILE,
                        now,
                        now
                ), new SourceResponseDTO(
                        source2,
                        notebookId,
                        groupB,
                        null,
                        "Bell History Blog",
                        false,
                        "https://example.com",
                        SourceStatus.READY,
                        SourceType.WEBSITE,
                        now,
                        now
                ), new SourceResponseDTO(
                        source3,
                        notebookId,
                        groupA,
                        null,
                        "Unindexed Draft",
                        false,
                        null,
                        SourceStatus.PENDING,
                        SourceType.TEXT,
                        now,
                        now
                )
        ));

        NotebookAgentTools tools = createTools(notebookId, List.of(), recorder);

        String result = tools.listSources("Bell", groupA.toString(), "Find Bell sources in Primary Papers");

        assertThat(result)
                .contains("Primary Papers")
                .contains("Bell Inequality Paper")
                .doesNotContain("Bell History Blog")
                .doesNotContain("Unindexed Draft");
        AgentStep step = recorder.getStepsSnapshot().getFirst();
        assertThat(step.type()).isEqualTo("context");
        assertThat(step.status()).isEqualTo("done");
    }

    @Test
    @DisplayName("getNotebookOverview should return notebook summary and record context step")
    void getNotebookOverviewReturnsSummary() {
        UUID notebookId = UUID.randomUUID();
        AgentTraceRecorder recorder = new AgentTraceRecorder(null, null);

        Notebook notebook = new Notebook();
        notebook.setId(notebookId);
        notebook.setSummary("This notebook covers quantum computing basics.");
        notebook.setSummaryTopics("[\"Qubits\",\"Entanglement\"]");
        when(notebookRepository.findById(notebookId)).thenReturn(Optional.of(notebook));

        NotebookAgentTools tools = createTools(notebookId, List.of(), recorder);

        String result = tools.getNotebookOverview("Check high-level topics");

        assertThat(result).contains("quantum computing basics").contains("Qubits");
        assertThat(recorder.getStepsSnapshot().getFirst().type()).isEqualTo("context");
        assertThat(recorder.getStepsSnapshot().getFirst().status()).isEqualTo("done");
    }

    @Test
    @DisplayName("calculate should evaluate expression deterministically and record calculate step")
    void calculateEvaluatesAndRecordsStep() {
        UUID notebookId = UUID.randomUUID();
        AgentTraceRecorder recorder = new AgentTraceRecorder(null, null);
        NotebookAgentTools tools = createTools(notebookId, List.of(), recorder);

        String result = tools.calculate("(120 + 30) / 4", "Compute average score");

        assertThat(result).contains("Result: 37.5");
        AgentStep step = recorder.getStepsSnapshot().getFirst();
        assertThat(step.type()).isEqualTo("calculate");
        assertThat(step.result()).isEqualTo("= 37.5");
        assertThat(step.status()).isEqualTo("done");
    }

    @Test
    @DisplayName("calculate should record error step on invalid expression")
    void calculateRecordsErrorOnInvalidExpression() {
        UUID notebookId = UUID.randomUUID();
        AgentTraceRecorder recorder = new AgentTraceRecorder(null, null);
        NotebookAgentTools tools = createTools(notebookId, List.of(), recorder);

        String result = tools.calculate("10 / 0", "Divide by zero");

        assertThat(result).contains("Could not evaluate expression").contains("Division by zero");
        AgentStep step = recorder.getStepsSnapshot().getFirst();
        assertThat(step.type()).isEqualTo("calculate");
        assertThat(step.status()).isEqualTo("error");
    }
}
