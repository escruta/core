package com.escruta.core.services;

import com.escruta.core.dtos.AgentStep;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.Source;
import com.escruta.core.entities.SourceChunk;
import com.escruta.core.entities.enums.SourceType;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.SourceChunkRepository;
import com.escruta.core.repositories.SourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.data.domain.PageRequest;

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
                Map.of("sourceId", sourceId.toString(), "title", "Quantum Lecture")
        );
        when(retrievalService.search(notebookId, List.of(sourceId), "Bell theorem", 6)).thenReturn(List.of(doc));

        NotebookAgentTools tools = new NotebookAgentTools(
                notebookId,
                List.of(sourceId),
                retrievalService,
                sourceRepository,
                chunkRepository,
                notebookRepository,
                notebookContextService,
                recorder
        );

        String response = tools.searchSources("Bell theorem", "Find definition of Bell theorem");

        assertThat(response).contains("Source ID: " + chunkId).contains("Quantum Lecture");
        assertThat(tools.getCollectedDocuments()).hasSize(1);
        List<AgentStep> steps = recorder.getStepsSnapshot();
        assertThat(steps).hasSize(1);
        assertThat(steps.getFirst().type()).isEqualTo("search");
        assertThat(steps.getFirst().status()).isEqualTo("done");
        assertThat(steps.getFirst().sources()).containsExactly("Quantum Lecture");
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

        SourceChunk chunk = new SourceChunk();
        chunk.setId(chunkId);
        chunk.setSourceId(sourceId);
        chunk.setNotebookId(notebookId);
        chunk.setChunkIndex(0);
        chunk.setTitle("Paper on Entanglement");
        chunk.setContent("Opening paragraph about entanglement.");

        when(sourceRepository.findByIdAndNotebookId(sourceId, notebookId)).thenReturn(Optional.of(source));
        when(chunkRepository.findByNotebookIdAndSourceIdInOrderByChunkIndexAsc(
                notebookId,
                List.of(sourceId),
                PageRequest.of(0, 3)
        )).thenReturn(List.of(chunk));

        NotebookAgentTools tools = new NotebookAgentTools(
                notebookId,
                List.of(sourceId),
                retrievalService,
                sourceRepository,
                chunkRepository,
                notebookRepository,
                notebookContextService,
                recorder
        );

        String result = tools.getSourceOverview(sourceId.toString(), "Understand the paper structure");

        assertThat(result).contains("Overview of Bell inequalities.").contains("Source ID: " + chunkId);
        assertThat(tools.getCollectedDocuments()).hasSize(1);
        AgentStep step = recorder.getStepsSnapshot().getFirst();
        assertThat(step.type()).isEqualTo("read");
        assertThat(step.detail()).isEqualTo("Paper on Entanglement");
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

        NotebookAgentTools tools = new NotebookAgentTools(
                notebookId,
                List.of(),
                retrievalService,
                sourceRepository,
                chunkRepository,
                notebookRepository,
                notebookContextService,
                recorder
        );

        String result = tools.getNotebookOverview("Check high-level topics");

        assertThat(result).contains("quantum computing basics").contains("Qubits");
        assertThat(recorder.getStepsSnapshot().getFirst().type()).isEqualTo("context");
        assertThat(recorder.getStepsSnapshot().getFirst().status()).isEqualTo("done");
    }
}
