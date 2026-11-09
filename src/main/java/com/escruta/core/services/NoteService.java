package com.escruta.core.services;

import com.escruta.core.dtos.note.NoteCreationDTO;
import com.escruta.core.dtos.note.NoteResponseDTO;
import com.escruta.core.dtos.note.NoteUpdateDTO;
import com.escruta.core.dtos.note.NoteWithContentDTO;
import com.escruta.core.entities.Note;
import com.escruta.core.entities.Notebook;
import com.escruta.core.mappers.NoteMapper;
import com.escruta.core.repositories.NoteRepository;
import com.escruta.core.repositories.NotebookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NoteService {
    private final NoteRepository noteRepository;
    private final NotebookRepository notebookRepository;
    private final UserService userService;
    private final NoteMapper noteMapper;

    public List<NoteResponseDTO> getNotes(UUID notebookId) {
        UUID userId = userService.getUserId();
        if (userId == null)
            return List.of();

        if (notebookId != null) {
            return noteRepository
                    .findByNotebookIdAndNotebook_UserId(notebookId, userId)
                    .stream()
                    .map(NoteResponseDTO::new)
                    .toList();
        }
        return noteRepository.findByNotebook_UserId(userId).stream().map(NoteResponseDTO::new).toList();
    }

    public NoteWithContentDTO getNote(UUID noteId) {
        UUID userId = userService.getUserId();
        if (userId == null) {
            return null;
        }
        Optional<Note> note = noteRepository.findByIdAndNotebook_UserId(noteId, userId);
        return note.map(NoteWithContentDTO::new).orElse(null);
    }

    @Transactional
    public NoteResponseDTO addNote(NoteCreationDTO newNoteDto) {
        if (newNoteDto.notebookId() == null)
            return null;

        UUID userId = userService.getUserId();
        if (userId == null) {
            return null;
        }

        Optional<Notebook> notebookOptional = notebookRepository.findByIdAndUserId(newNoteDto.notebookId(), userId);
        if (notebookOptional.isEmpty())
            return null;

        Note note = noteMapper.toNote(newNoteDto, notebookOptional.get());
        noteRepository.save(note);
        notebookRepository.touchLastActivity(newNoteDto.notebookId());
        return new NoteResponseDTO(note);
    }

    @Transactional
    public NoteResponseDTO updateNote(NoteUpdateDTO newNoteDto) {
        UUID userId = userService.getUserId();
        if (userId == null) {
            return null;
        }

        Optional<Note> noteOptional = noteRepository.findByIdAndNotebook_UserId(
                UUID.fromString(newNoteDto.id()),
                userId
        );
        if (noteOptional.isPresent()) {
            Note note = noteOptional.get();
            UUID notebookId = note.getNotebook().getId();
            noteMapper.updateNoteFromDto(newNoteDto, note);
            noteRepository.save(note);
            notebookRepository.touchLastActivity(notebookId);
            return new NoteResponseDTO(note);
        }
        return null;
    }

    @Transactional
    public NoteResponseDTO deleteNote(UUID noteId) {
        UUID userId = userService.getUserId();
        if (userId == null) {
            return null;
        }

        Optional<Note> noteOptional = noteRepository.findByIdAndNotebook_UserId(noteId, userId);
        if (noteOptional.isPresent()) {
            Note note = noteOptional.get();
            UUID notebookId = note.getNotebook().getId();
            noteRepository.deleteById(noteId);
            notebookRepository.touchLastActivity(notebookId);
            return new NoteResponseDTO(note);
        }
        return null;
    }
}
