package com.escruta.core.controllers;

import com.escruta.core.dtos.source.SourceCreationDTO;
import com.escruta.core.dtos.source.SourceFileCreationDTO;
import com.escruta.core.dtos.source.SourceResponseDTO;
import com.escruta.core.dtos.source.SourceUpdateDTO;
import com.escruta.core.dtos.source.SourceWithContentDTO;
import com.escruta.core.services.SourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("notebooks/{notebookId}/sources")
@RequiredArgsConstructor
public class SourceController {
    private final SourceService sourceService;

    @GetMapping
    public ResponseEntity<List<SourceResponseDTO>> getNotebookSources(
            @PathVariable UUID notebookId
    ) {
        return ResponseEntity.ok(sourceService.getSources(notebookId));
    }

    @GetMapping("{sourceId}")
    public ResponseEntity<SourceWithContentDTO> getNotebookSource(
            @PathVariable UUID notebookId,
            @PathVariable UUID sourceId
    ) {
        var source = sourceService.getSource(notebookId, sourceId);
        return source != null ?
                ResponseEntity.ok(source) :
                ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<SourceWithContentDTO> createNotebookSource(
            @PathVariable UUID notebookId,
            @Valid @RequestBody SourceCreationDTO sourceCreationDTO,
            @RequestParam(name = "aiConverter", defaultValue = "false") boolean aiConverter
    ) {
        var source = sourceService.addSource(notebookId, sourceCreationDTO, aiConverter);
        return source != null ?
                ResponseEntity.status(HttpStatus.CREATED).body(source) :
                ResponseEntity.badRequest().build();
    }

    @PostMapping("/upload")
    public ResponseEntity<?> createNotebookSourceFromFile(
            @PathVariable UUID notebookId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(name = "icon", required = false) String icon,
            @RequestParam(name = "aiConverter", defaultValue = "false") boolean aiConverter
    ) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be empty");
        }
        var sourceFileCreationDTO = new SourceFileCreationDTO(icon, title.trim());

        var source = sourceService.addSourceFromFile(notebookId, sourceFileCreationDTO, file, aiConverter);
        return source != null ?
                ResponseEntity.status(HttpStatus.CREATED).body(source) :
                ResponseEntity.badRequest().build();
    }

    @PutMapping
    public ResponseEntity<SourceResponseDTO> updateNotebookSource(
            @PathVariable UUID notebookId,
            @Valid @RequestBody SourceUpdateDTO sourceUpdateDTO
    ) {
        var source = sourceService.updateSource(notebookId, sourceUpdateDTO);
        return source != null ?
                ResponseEntity.ok(source) :
                ResponseEntity.notFound().build();
    }

    @DeleteMapping("{sourceId}")
    public ResponseEntity<SourceResponseDTO> deleteNotebookSource(
            @PathVariable UUID notebookId,
            @PathVariable UUID sourceId
    ) {
        var source = sourceService.deleteSource(notebookId, sourceId);
        return source != null ?
                ResponseEntity.ok(source) :
                ResponseEntity.notFound().build();
    }

    @PostMapping("{sourceId}/summary")
    public ResponseEntity<String> generateSourceSummary(@PathVariable UUID notebookId, @PathVariable UUID sourceId) {
        String summary = sourceService.generateSummary(notebookId, sourceId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("{sourceId}/summary")
    public ResponseEntity<String> getSourceSummary(@PathVariable UUID notebookId, @PathVariable UUID sourceId) {
        String summary = sourceService.getSummary(notebookId, sourceId);
        return ResponseEntity.ok(summary);
    }

    @DeleteMapping("{sourceId}/summary")
    public ResponseEntity<Void> deleteSourceSummary(@PathVariable UUID notebookId, @PathVariable UUID sourceId) {
        boolean deleted = sourceService.deleteSummary(notebookId, sourceId);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
