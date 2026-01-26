package com.escruta.core.dtos.tools;

import java.util.List;

public record MindMapResponse(
        String central,
        List<Branch> branches
) {
    public record Branch(
            String label,
            List<Branch> children
    ) {
    }
}
