package com.escruta.core.dtos;

import java.util.List;

public record AgentStep(
        String id,
        String type,
        String title,
        String detail,
        String rationale,
        String result,
        List<String> sources,
        String status,
        Long durationMs
) {
    public static AgentStep running(String id, String type, String title, String detail, String rationale) {
        return new AgentStep(id, type, title, detail, rationale, null, null, "running", null);
    }

    public static AgentStep done(
            String id,
            String type,
            String title,
            String detail,
            String rationale,
            String result,
            List<String> sources,
            Long durationMs
    ) {
        return new AgentStep(id, type, title, detail, rationale, result, sources, "done", durationMs);
    }

    public AgentStep withDetail(String newDetail) {
        return new AgentStep(id, type, title, newDetail, rationale, result, sources, status, durationMs);
    }

    public AgentStep complete(String newResult, List<String> newSources, Long newDurationMs) {
        return new AgentStep(id, type, title, detail, rationale, newResult, newSources, "done", newDurationMs);
    }

    public AgentStep fail(String errorMessage, Long newDurationMs) {
        return new AgentStep(id, type, title, detail, rationale, errorMessage, sources, "error", newDurationMs);
    }
}
