package com.escruta.core.controllers;

import com.escruta.core.dtos.note.NoteCreationDTO;
import com.escruta.core.dtos.note.NoteResponseDTO;
import com.escruta.core.dtos.note.NoteUpdateDTO;
import com.escruta.core.dtos.note.NoteWithContentDTO;
import com.escruta.core.services.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("notes")
@RequiredArgsConstructor
public class NoteController {
    private final NoteService noteService;

    @GetMapping
    public ResponseEntity<List<NoteResponseDTO>> getNotes(
            @RequestParam(required = false) UUID notebookId
    ) {
        return ResponseEntity.ok(noteService.getNotes(notebookId));
    }

    @GetMapping("{noteId}")
    public ResponseEntity<NoteWithContentDTO> getNoteContent(
            @PathVariable UUID noteId
    ) {
        var note = noteService.getNote(noteId);
        return note != null ?
                ResponseEntity.ok(note) :
                ResponseEntity.notFound().build();

    }

    @PostMapping
    public ResponseEntity<NoteResponseDTO> createNote(
            @Valid @RequestBody NoteCreationDTO noteCreationDTO
    ) {
        var note = noteService.addNote(noteCreationDTO);
        return note != null ?
                ResponseEntity.status(HttpStatus.CREATED).body(note) :
                ResponseEntity.badRequest().build();
    }

    @PutMapping
    public ResponseEntity<NoteResponseDTO> updateNote(
            @Valid @RequestBody NoteUpdateDTO noteUpdateDTO
    ) {
        var note = noteService.updateNote(noteUpdateDTO);
        return note != null ?
                ResponseEntity.ok(note) :
                ResponseEntity.notFound().build();
    }

    @DeleteMapping("{noteId}")
    public ResponseEntity<NoteResponseDTO> deleteNote(
            @PathVariable UUID noteId
    ) {
        var note = noteService.deleteNote(noteId);
        return note != null ?
                ResponseEntity.ok(note) :
                ResponseEntity.notFound().build();
    }
}
