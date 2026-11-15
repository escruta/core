package com.escruta.core.dtos.tools;

import java.util.List;

public record MindMapResponse(
        String central,
        String description,
        List<Branch> branches
) {
    public record Branch(
            String label,
            String description,
            List<Branch> children
    ) {
    }
}
