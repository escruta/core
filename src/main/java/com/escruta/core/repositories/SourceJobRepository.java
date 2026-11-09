package com.escruta.core.repositories;

import com.escruta.core.entities.SourceJob;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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

    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update SourceJob j
            set j.status = com.escruta.core.entities.SourceJob.JobStatus.PROCESSING,
                j.updatedAt = CURRENT_TIMESTAMP
            where j.id = :jobId
              and (j.status = com.escruta.core.entities.SourceJob.JobStatus.PENDING
                   or (j.status = com.escruta.core.entities.SourceJob.JobStatus.PROCESSING
                       and j.updatedAt < :staleBefore))
            """)
    int claim(UUID jobId, java.time.Instant staleBefore);

    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update SourceJob j
            set j.status = com.escruta.core.entities.SourceJob.JobStatus.PENDING,
                j.updatedAt = CURRENT_TIMESTAMP
            where j.status = com.escruta.core.entities.SourceJob.JobStatus.PROCESSING
            """)
    int requeueProcessingJobs();
}
