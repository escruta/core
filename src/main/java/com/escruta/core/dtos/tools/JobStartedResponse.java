package com.escruta.core.dtos.tools;

import java.util.UUID;

public record JobStartedResponse(
        UUID jobId,
        String message,
        GenerationJobResponse job
) {
    public JobStartedResponse(UUID jobId, String message) {
        this(jobId, message, null);
    }
}
