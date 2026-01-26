package com.escruta.core.dtos.tools;

import java.util.UUID;

public record JobStartedResponse(
        UUID jobId,
        String message
) {
}
