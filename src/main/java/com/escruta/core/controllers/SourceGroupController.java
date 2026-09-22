package com.escruta.core.controllers;

import com.escruta.core.dtos.sourcegroup.SourceGroupCreationDTO;
import com.escruta.core.dtos.sourcegroup.SourceGroupResponseDTO;
import com.escruta.core.dtos.sourcegroup.SourceGroupUpdateDTO;
import com.escruta.core.services.SourceGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("notebooks/{notebookId}/source-groups")
@RequiredArgsConstructor
public class SourceGroupController {
    private final SourceGroupService sourceGroupService;

    @GetMapping
    @PreAuthorize("@notebookOwnershipService.isUserNotebookOwner(#notebookId)")
    public ResponseEntity<List<SourceGroupResponseDTO>> getSourceGroups(
            @PathVariable UUID notebookId
    ) {
        return ResponseEntity.ok(sourceGroupService.getGroups(notebookId));
    }

    @PostMapping
    @PreAuthorize("@notebookOwnershipService.isUserNotebookOwner(#notebookId)")
    public ResponseEntity<SourceGroupResponseDTO> createSourceGroup(
            @PathVariable UUID notebookId,
            @Valid @RequestBody
            SourceGroupCreationDTO creationDTO
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sourceGroupService.createGroup(notebookId, creationDTO));
    }

    @PatchMapping("{groupId}")
    @PreAuthorize("@notebookOwnershipService.isUserNotebookOwner(#notebookId)")
    public ResponseEntity<SourceGroupResponseDTO> updateSourceGroup(
            @PathVariable UUID notebookId,
            @PathVariable UUID groupId,
            @Valid @RequestBody SourceGroupUpdateDTO updateDTO
    ) {
        return sourceGroupService
                .updateGroup(notebookId, groupId, updateDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("{groupId}")
    @PreAuthorize("@notebookOwnershipService.isUserNotebookOwner(#notebookId)")
    public ResponseEntity<Void> deleteSourceGroup(@PathVariable UUID notebookId, @PathVariable UUID groupId) {
        if (sourceGroupService.deleteGroup(notebookId, groupId)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
