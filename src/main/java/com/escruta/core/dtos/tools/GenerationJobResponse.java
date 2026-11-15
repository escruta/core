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
        String title,
        JobStatus status,
        GenerationOptions options,
        String result,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
    public static GenerationJobResponse from(GenerationJob job, GenerationOptions parsedOptions) {
        return new GenerationJobResponse(
                job.getId(),
                job.getNotebook().getId(),
                job.getType(),
                job.getTitle(),
                job.getStatus(),
                parsedOptions,
                job.getResult(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getCompletedAt()
        );
    }

    public static GenerationJobResponse from(GenerationJob job) {
        return from(job, null);
    }
}
