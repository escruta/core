package com.escruta.core.dtos;

import java.util.List;

public record SearchResponse(
        List<SearchResult> results
) {
}
