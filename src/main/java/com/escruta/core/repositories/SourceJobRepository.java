package com.escruta.core.repositories;

import com.escruta.core.entities.SourceJob;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SourceJobRepository extends CrudRepository<SourceJob, UUID> {
    List<SourceJob> findByStatusIn(List<SourceJob.JobStatus> statuses);

    Optional<SourceJob> findFirstBySourceIdAndTypeAndStatusInOrderByCreatedAtDesc(
            UUID sourceId,
            SourceJob.JobType type,
            List<SourceJob.JobStatus> statuses
    );

    Optional<SourceJob> findFirstByNotebookIdAndTypeAndStatusInOrderByCreatedAtDesc(
            UUID notebookId,
            SourceJob.JobType type,
            List<SourceJob.JobStatus> statuses
    );
}
