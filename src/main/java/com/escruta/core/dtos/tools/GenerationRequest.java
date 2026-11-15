package com.escruta.core.dtos.tools;

import com.escruta.core.entities.GenerationJob.JobType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GenerationRequest(
        @NotNull
        JobType type,
        @Size(max = 255)
        String title,
        @Valid
        GenerationOptions options
) {
    public GenerationRequest(JobType type) {
        this(type, null, null);
    }
}
