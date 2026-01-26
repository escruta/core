package com.escruta.core.controllers;

import com.escruta.core.dtos.tools.GenerationJobResponse;
import com.escruta.core.dtos.tools.GenerationRequest;
import com.escruta.core.dtos.tools.JobStartedResponse;
import com.escruta.core.entities.GenerationJob;
import com.escruta.core.entities.User;
import com.escruta.core.services.ToolsGenerationService;
import com.escruta.core.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("notebooks/{notebookId}/tools")
@RequiredArgsConstructor
public class ToolsController {
    private final ToolsGenerationService generationService;
    private final UserService userService;

    @PostMapping("generate")
    public ResponseEntity<?> startGeneration(
            @PathVariable UUID notebookId,
            @Valid @RequestBody GenerationRequest request
    ) {
        User user = userService.getCurrentFullUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not authenticated");
        }

        try {
            GenerationJob job = generationService.createJob(notebookId, user, request.type());
            generationService.processJob(job.getId());
            return ResponseEntity
                    .accepted()
                    .body(new JobStartedResponse(
                            job.getId(),
                            "Generation started. Poll /jobs/" + job.getId() + " for status."
                    ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("jobs/{jobId}")
    public ResponseEntity<?> getJobStatus(@PathVariable UUID notebookId, @PathVariable UUID jobId) {
        User user = userService.getCurrentFullUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not authenticated");
        }

        return generationService
                .getJob(jobId, user.getId())
                .map(job -> ResponseEntity.ok(GenerationJobResponse.from(job)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("jobs")
    public ResponseEntity<?> getAllJobs(@PathVariable UUID notebookId) {
        User user = userService.getCurrentFullUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not authenticated");
        }

        List<GenerationJobResponse> jobs = generationService
                .getJobsForNotebook(notebookId, user.getId())
                .stream()
                .map(GenerationJobResponse::from)
                .toList();

        return ResponseEntity.ok(jobs);
    }

    @GetMapping("jobs/latest/{type}")
    public ResponseEntity<?> getLatestJob(@PathVariable UUID notebookId, @PathVariable GenerationJob.JobType type) {
        User user = userService.getCurrentFullUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not authenticated");
        }

        List<GenerationJob> activeJobs = generationService.getActiveJobs(notebookId, user.getId(), type);
        if (!activeJobs.isEmpty()) {
            return ResponseEntity.ok(GenerationJobResponse.from(activeJobs.getFirst()));
        }

        return generationService
                .getLatestCompletedJob(notebookId, user.getId(), type)
                .map(job -> ResponseEntity.ok(GenerationJobResponse.from(job)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
