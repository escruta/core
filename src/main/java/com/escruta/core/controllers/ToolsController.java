package com.escruta.core.controllers;

import com.escruta.core.dtos.tools.GenerationJobResponse;
import com.escruta.core.dtos.tools.GenerationJobUpdateDTO;
import com.escruta.core.dtos.tools.GenerationOptions;
import com.escruta.core.dtos.tools.GenerationRequest;
import com.escruta.core.dtos.tools.JobStartedResponse;
import com.escruta.core.entities.GenerationJob;
import com.escruta.core.entities.User;
import com.escruta.core.services.RateLimitService;
import com.escruta.core.services.ToolsGenerationService;
import com.escruta.core.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("notebooks/{notebookId}/tools")
@RequiredArgsConstructor
public class ToolsController {
    private final ToolsGenerationService generationService;
    private final UserService userService;
    private final RateLimitService rateLimitService;

    @PostMapping("generate")
    public ResponseEntity<JobStartedResponse> startGeneration(
            @PathVariable UUID notebookId,
            @Valid @RequestBody GenerationRequest request
    ) {
        User user = requireAuthenticatedUser();
        rateLimitService.checkChatRequest(user.getId());

        try {
            GenerationJob job = generationService.createAndDispatchJob(notebookId, user, request);
            return ResponseEntity.accepted().body(new JobStartedResponse(
                    job.getId(),
                    "Generation started. It will be published via SSE when ready.",
                    generationService.toResponse(job)
            ));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("jobs/{jobId}/retry")
    public ResponseEntity<GenerationJobResponse> retryJob(
            @PathVariable UUID notebookId,
            @PathVariable UUID jobId,
            @Valid @RequestBody(required = false) GenerationOptions options
    ) {
        User user = requireAuthenticatedUser();
        rateLimitService.checkChatRequest(user.getId());

        try {
            GenerationJob job = generationService.retryJob(notebookId, jobId, user, options);
            return ResponseEntity.accepted().body(generationService.toResponse(job));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("jobs/{jobId}")
    public ResponseEntity<GenerationJobResponse> getJobStatus(@PathVariable UUID notebookId, @PathVariable UUID jobId) {
        User user = requireAuthenticatedUser();

        return generationService
                .getJob(notebookId, jobId, user.getId())
                .map(job -> ResponseEntity.ok(generationService.toResponse(job)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("jobs/{jobId}")
    public ResponseEntity<GenerationJobResponse> renameJob(
            @PathVariable UUID notebookId,
            @PathVariable UUID jobId,
            @Valid @RequestBody GenerationJobUpdateDTO updateDTO
    ) {
        User user = requireAuthenticatedUser();

        return generationService
                .renameJob(notebookId, jobId, user.getId(), updateDTO.title())
                .map(job -> ResponseEntity.ok(generationService.toResponse(job)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("jobs/{jobId}")
    public ResponseEntity<Void> deleteJob(@PathVariable UUID notebookId, @PathVariable UUID jobId) {
        User user = requireAuthenticatedUser();

        if (!generationService.deleteJob(notebookId, jobId, user.getId())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("jobs")
    public ResponseEntity<List<GenerationJobResponse>> getAllJobs(
            @PathVariable UUID notebookId,
            @RequestParam(required = false) GenerationJob.JobType type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "Newest") String sort,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        User user = requireAuthenticatedUser();

        List<GenerationJobResponse> jobs = generationService
                .getJobsForNotebook(notebookId, user.getId(), type, search, sort, limit, offset)
                .stream()
                .map(generationService::toResponse)
                .toList();

        return ResponseEntity.ok(jobs);
    }

    @GetMapping("jobs/latest/{type}")
    public ResponseEntity<GenerationJobResponse> getLatestJob(
            @PathVariable UUID notebookId,
            @PathVariable GenerationJob.JobType type
    ) {
        User user = requireAuthenticatedUser();

        List<GenerationJob> activeJobs = generationService.getActiveJobs(notebookId, user.getId(), type);
        if (!activeJobs.isEmpty()) {
            return ResponseEntity.ok(generationService.toResponse(activeJobs.getFirst()));
        }

        return generationService
                .getLatestCompletedJob(notebookId, user.getId(), type)
                .map(job -> ResponseEntity.ok(generationService.toResponse(job)))
                .orElseGet(() -> ResponseEntity.ok(null));
    }

    private User requireAuthenticatedUser() {
        User user = userService.getCurrentUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        return user;
    }
}
