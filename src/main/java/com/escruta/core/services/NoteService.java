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
        var currentUser = userService.getCurrentUser();
        if (currentUser == null)
            return List.of();

        if (notebookId != null) {
            return noteRepository
                    .findByNotebookIdAndNotebook_UserId(notebookId, currentUser.getId())
                    .stream()
                    .map(NoteResponseDTO::new)
                    .toList();
        }
        return noteRepository.findByNotebook_UserId(currentUser.getId()).stream().map(NoteResponseDTO::new).toList();
    }

    public NoteWithContentDTO getNote(UUID noteId) {
        Optional<Note> note = noteRepository.findById(noteId);
        return note.map(NoteWithContentDTO::new).orElse(null);
    }

    public NoteResponseDTO addNote(NoteCreationDTO newNoteDto) {
        if (newNoteDto.notebookId() == null)
            return null;

        Optional<Notebook> notebookOptional = notebookRepository.findById(newNoteDto.notebookId());
        if (notebookOptional.isEmpty())
            return null;

        Note note = noteMapper.toNote(newNoteDto, notebookOptional.get());
        noteRepository.save(note);
        notebookRepository.touchLastActivity(newNoteDto.notebookId());
        return new NoteResponseDTO(note);
    }

    public NoteResponseDTO updateNote(NoteUpdateDTO newNoteDto) {
        Optional<Note> noteOptional = noteRepository.findById(UUID.fromString(newNoteDto.id()));
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

    public NoteResponseDTO deleteNote(UUID noteId) {
        Optional<Note> noteOptional = noteRepository.findById(noteId);
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
