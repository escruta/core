package com.escruta.core.integration;

import com.escruta.core.dtos.notebook.NotebookResponseDTO;
import com.escruta.core.dtos.source.SourceResponseDTO;
import com.escruta.core.entities.GenerationJob;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.Source;
import com.escruta.core.entities.SourceJob;
import com.escruta.core.entities.User;
import com.escruta.core.entities.enums.SourceStatus;
import com.escruta.core.entities.enums.SourceType;
import com.escruta.core.repositories.GenerationJobRepository;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.OffsetPageRequest;
import com.escruta.core.repositories.SourceJobRepository;
import com.escruta.core.repositories.SourceRepository;
import com.escruta.core.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Repository query integration tests")
class RepositoryQueryIntegrationTest {
    @Autowired
    private NotebookRepository notebookRepository;
    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private GenerationJobRepository generationJobRepository;
    @Autowired
    private SourceJobRepository sourceJobRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Projections, ownership queries and job claiming work against MariaDB")
    void repositoryQueries_shouldWork() {
        User user = new User();
        user.setName("Query Test");
        user.setEmail("query-" + UUID.randomUUID() + "@example.com");
        user = userRepository.save(user);

        Notebook notebook = new Notebook();
        notebook.setUser(user);
        notebook.setTitle("Query Notebook");
        notebook.setIcon("📓");
        notebook = notebookRepository.save(notebook);

        Source source = new Source();
        source.setNotebook(notebook);
        source.setTitle("Query Source");
        source.setContent("some content");
        source.setStatus(SourceStatus.READY);
        source.setType(SourceType.TEXT);
        source = sourceRepository.save(source);

        SourceJob sourceJob = new SourceJob(source, notebook, SourceJob.JobType.EXTRACT);
        sourceJob = sourceJobRepository.save(sourceJob);

        GenerationJob generationJob = new GenerationJob(notebook, user, GenerationJob.JobType.STUDY_GUIDE);
        generationJob = generationJobRepository.save(generationJob);

        List<NotebookResponseDTO> notebooks = notebookRepository.findResponsesByUserId(user.getId());
        assertThat(notebooks).hasSize(1);
        assertThat(notebooks.getFirst().title()).isEqualTo("Query Notebook");

        List<NotebookResponseDTO> filtered = notebookRepository.findResponsesByUserIdAndTitle(
                user.getId(),
                "Query",
                new OffsetPageRequest(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        assertThat(filtered).hasSize(1);

        List<SourceResponseDTO> sources = sourceRepository.findSummariesByNotebookId(notebook.getId());
        assertThat(sources).hasSize(1);
        assertThat(sources.getFirst().title()).isEqualTo("Query Source");
        assertThat(sourceRepository.findResponseByIdAndNotebookId(source.getId(), notebook.getId()))
                .isPresent()
                .get()
                .extracting(SourceResponseDTO::title)
                .isEqualTo("Query Source");

        assertThat(sourceRepository.findStatusById(source.getId())).contains(SourceStatus.READY);
        assertThat(sourceRepository.findSummaryTextByIdAndNotebookId(source.getId(), notebook.getId())).isEmpty();
        assertThat(notebookRepository.findOwnerId(notebook.getId())).isEqualTo(user.getId());
        assertThat(notebookRepository.existsByIdAndUserId(notebook.getId(), user.getId())).isTrue();

        assertThat(generationJobRepository.claimPending(generationJob.getId())).isEqualTo(1);
        assertThat(generationJobRepository.claimPending(generationJob.getId())).isZero();

        Instant staleBefore = Instant.now().minus(1, ChronoUnit.HOURS);
        assertThat(sourceJobRepository.claim(sourceJob.getId(), staleBefore)).isEqualTo(1);
        assertThat(sourceJobRepository.claim(sourceJob.getId(), staleBefore)).isZero();

        assertThat(notebookRepository.touchLastActivity(notebook.getId())).isEqualTo(1);
        assertThat(notebookRepository.touchLastActivity(notebook.getId())).isZero();

        assertThat(sourceRepository.clearSummaryByIdAndNotebookId(source.getId(), notebook.getId())).isEqualTo(1);
    }
}
