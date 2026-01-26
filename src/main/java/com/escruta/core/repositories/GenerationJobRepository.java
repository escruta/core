package com.escruta.core.repositories;

import com.escruta.core.entities.GenerationJob;
import com.escruta.core.entities.GenerationJob.JobStatus;
import com.escruta.core.entities.GenerationJob.JobType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {
    List<GenerationJob> findByNotebookIdAndUserIdOrderByCreatedAtDesc(UUID notebookId, UUID userId);

    Optional<GenerationJob> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT j FROM GenerationJob j WHERE j.notebook.id = :notebookId AND j.user.id = :userId " + "AND j.type = :type AND j.status IN :statuses ORDER BY j.createdAt DESC")
    List<GenerationJob> findActiveJobsByType(UUID notebookId, UUID userId, JobType type, List<JobStatus> statuses);

    @Query("SELECT j FROM GenerationJob j WHERE j.notebook.id = :notebookId AND j.user.id = :userId " + "AND j.type = :type AND j.status = 'COMPLETED' ORDER BY j.createdAt DESC LIMIT 1")
    Optional<GenerationJob> findLatestCompletedByType(UUID notebookId, UUID userId, JobType type);

    boolean existsByNotebookIdAndUserIdAndTypeAndStatusIn(
            UUID notebookId,
            UUID userId,
            JobType type,
            List<JobStatus> statuses
    );
}
