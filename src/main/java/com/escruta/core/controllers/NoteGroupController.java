package com.escruta.core.controllers;

import com.escruta.core.dtos.notegroup.NoteGroupCreationDTO;
import com.escruta.core.dtos.notegroup.NoteGroupResponseDTO;
import com.escruta.core.dtos.notegroup.NoteGroupUpdateDTO;
import com.escruta.core.services.NoteGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("notebooks/{notebookId}/note-groups")
@RequiredArgsConstructor
public class NoteGroupController {
    private final NoteGroupService noteGroupService;

    @GetMapping
    public ResponseEntity<List<NoteGroupResponseDTO>> getNoteGroups(
            @PathVariable UUID notebookId
    ) {
        return ResponseEntity.ok(noteGroupService.getGroups(notebookId));
    }

    @PostMapping
    public ResponseEntity<NoteGroupResponseDTO> createNoteGroup(
            @PathVariable UUID notebookId,
            @Valid @RequestBody NoteGroupCreationDTO creationDTO
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noteGroupService.createGroup(notebookId, creationDTO));
    }

    @PatchMapping("{groupId}")
    public ResponseEntity<NoteGroupResponseDTO> updateNoteGroup(
            @PathVariable UUID notebookId,
            @PathVariable UUID groupId,
            @Valid @RequestBody NoteGroupUpdateDTO updateDTO
    ) {
        return noteGroupService
                .updateGroup(notebookId, groupId, updateDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("{groupId}")
    public ResponseEntity<Void> deleteNoteGroup(@PathVariable UUID notebookId, @PathVariable UUID groupId) {
        if (noteGroupService.deleteGroup(notebookId, groupId)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
