package com.escruta.core.controllers;

import com.escruta.core.dtos.folder.FolderCreationDTO;
import com.escruta.core.dtos.folder.FolderResponseDTO;
import com.escruta.core.dtos.folder.FolderUpdateDTO;
import com.escruta.core.services.FolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @GetMapping
    public ResponseEntity<List<FolderResponseDTO>> getFolders() {
        return ResponseEntity.ok(folderService.getFoldersForCurrentUser());
    }

    @PostMapping
    public ResponseEntity<FolderResponseDTO> createFolder(@Valid @RequestBody FolderCreationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(folderService.createFolder(dto));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<FolderResponseDTO> updateFolder(@PathVariable UUID id, @Valid @RequestBody FolderUpdateDTO dto) {
        return folderService.updateFolder(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFolder(@PathVariable UUID id) {
        if (folderService.deleteFolder(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
