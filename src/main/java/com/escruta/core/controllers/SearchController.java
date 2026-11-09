package com.escruta.core.controllers;

import com.escruta.core.dtos.SearchRequest;
import com.escruta.core.dtos.SearchResponse;
import com.escruta.core.services.HelperService;
import com.escruta.core.services.RateLimitService;
import com.escruta.core.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("search")
@RequiredArgsConstructor
public class SearchController {
    private final HelperService helperService;
    private final RateLimitService rateLimitService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("@notebookOwnershipService.isUserNotebookOwner(#request.notebookId())")
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        rateLimitService.checkChatRequest(userService.getUserId());
        return helperService.search(request.query(), request.maxResults(), request.notebookId());
    }
}
