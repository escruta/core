package com.escruta.core.dtos.tools;

import com.escruta.core.entities.GenerationJob;
import com.escruta.core.entities.GenerationJob.JobStatus;
import com.escruta.core.entities.GenerationJob.JobType;

import java.time.Instant;
import java.util.UUID;

public record GenerationJobResponse(
        UUID id,
        UUID notebookId,
        JobType type,
        JobStatus status,
        String result,
        String errorMessage,
        Instant createdAt,
        Instant completedAt
) {
    public static GenerationJobResponse from(GenerationJob job) {
        return new GenerationJobResponse(
                job.getId(),
                job.getNotebook().getId(),
                job.getType(),
                job.getStatus(),
                job.getResult(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getCompletedAt()
        );
    }
}
