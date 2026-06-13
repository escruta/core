package com.escruta.core.controllers;

import com.escruta.core.dtos.SearchRequest;
import com.escruta.core.dtos.SearchResponse;
import com.escruta.core.services.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("search")
@RequiredArgsConstructor
public class SearchController {
    private final SearchService searchService;

    @PostMapping
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        return searchService.search(request.query(), request.maxResults());
    }
}
