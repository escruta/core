package com.escruta.core.repositories;

import com.escruta.core.entities.GenerationJob;
import com.escruta.core.entities.GenerationJob.JobStatus;
import com.escruta.core.entities.GenerationJob.JobType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {
    @EntityGraph(attributePaths = {"notebook"})
    List<GenerationJob> findByNotebookIdAndUserIdOrderByCreatedAtDesc(
            UUID notebookId,
            UUID userId,
            org.springframework.data.domain.Pageable pageable
    );

    @EntityGraph(attributePaths = {"notebook"})
    Optional<GenerationJob> findByIdAndUserId(UUID id, UUID userId);

    @Query("select j.user.id from GenerationJob j where j.id = :jobId")
    Optional<UUID> findUserIdById(UUID jobId);

    @Query("select j.notebook.id from GenerationJob j where j.id = :jobId")
    Optional<UUID> findNotebookIdById(UUID jobId);

    @EntityGraph(attributePaths = {"notebook"})
    @Query("SELECT j FROM GenerationJob j WHERE j.notebook.id = :notebookId AND j.user.id = :userId " + "AND j.type = :type AND j.status IN :statuses ORDER BY j.createdAt DESC")
    List<GenerationJob> findActiveJobsByType(UUID notebookId, UUID userId, JobType type, List<JobStatus> statuses);

    @EntityGraph(attributePaths = {"notebook"})
    @Query("SELECT j FROM GenerationJob j WHERE j.notebook.id = :notebookId AND j.user.id = :userId " + "AND j.type = :type AND j.status = 'COMPLETED' ORDER BY j.createdAt DESC LIMIT 1")
    Optional<GenerationJob> findLatestCompletedByType(UUID notebookId, UUID userId, JobType type);

    boolean existsByNotebookIdAndUserIdAndTypeAndStatusIn(
            UUID notebookId,
            UUID userId,
            JobType type,
            List<JobStatus> statuses
    );

    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update GenerationJob j
            set j.status = com.escruta.core.entities.GenerationJob.JobStatus.PROCESSING,
                j.updatedAt = CURRENT_TIMESTAMP
            where j.id = :jobId
              and j.status = com.escruta.core.entities.GenerationJob.JobStatus.PENDING
            """)
    int claimPending(UUID jobId);
}
