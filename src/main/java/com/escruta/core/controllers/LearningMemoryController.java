package com.escruta.core.controllers;

import com.escruta.core.dtos.memory.LearningMemoryDTO;
import com.escruta.core.dtos.memory.LearningMemoryUpdateDTO;
import com.escruta.core.dtos.memory.LearningSignalRequest;
import com.escruta.core.services.LearningMemoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("notebooks/{notebookId}/learning-memory")
@RequiredArgsConstructor
public class LearningMemoryController {
    private final LearningMemoryService learningMemoryService;

    @GetMapping
    public ResponseEntity<LearningMemoryDTO> getLearningMemory(@PathVariable UUID notebookId) {
        return ResponseEntity.ok(learningMemoryService.getMemory(notebookId));
    }

    @PutMapping
    public ResponseEntity<LearningMemoryDTO> updateLearningMemory(
            @PathVariable UUID notebookId,
            @Valid @RequestBody LearningMemoryUpdateDTO request
    ) {
        try {
            return ResponseEntity.ok(learningMemoryService.updateMemory(notebookId, request));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("signals")
    public ResponseEntity<LearningMemoryDTO> recordSignal(
            @PathVariable UUID notebookId,
            @Valid @RequestBody LearningSignalRequest request
    ) {
        try {
            return ResponseEntity.ok(learningMemoryService.recordPracticeSignal(notebookId, request));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping
    public ResponseEntity<LearningMemoryDTO> resetLearningMemory(@PathVariable UUID notebookId) {
        try {
            return ResponseEntity.ok(learningMemoryService.resetMemory(notebookId));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
