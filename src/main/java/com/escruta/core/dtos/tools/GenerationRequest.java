package com.escruta.core.dtos.tools;

import com.escruta.core.entities.GenerationJob.JobType;

import javax.validation.constraints.NotNull;

public record GenerationRequest(
        @NotNull
        JobType type
) {
}
