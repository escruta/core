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
        return noteRepository.findByNotebookId(notebookId).stream().map(NoteResponseDTO::new).toList();
    }

    public NoteWithContentDTO getNote(UUID notebookId, UUID noteId) {
        Optional<Note> note = noteRepository.findById(noteId);
        if (note.isEmpty() || !notebookRepository.existsById(notebookId)) {
            return null;
        }
        return note.map(NoteWithContentDTO::new).orElse(null);
    }

    public NoteResponseDTO addNote(UUID notebookId, NoteCreationDTO newNoteDto) {
        var currentUser = userService.getCurrentFullUser();
        Optional<Notebook> notebookOptional = notebookRepository.findById(notebookId);
        if (notebookOptional.isPresent() && currentUser != null) {
            Note note = noteMapper.toNote(newNoteDto, notebookOptional.get());
            noteRepository.save(note);
            return new NoteResponseDTO(note);
        }
        return null;
    }

    public NoteResponseDTO updateNote(UUID notebookId, NoteUpdateDTO newNoteDto) {
        Optional<Notebook> notebookOptional = notebookRepository.findById(notebookId);
        Optional<Note> noteOptional = noteRepository.findById(UUID.fromString(newNoteDto.id()));
        if (notebookOptional.isPresent() && noteOptional.isPresent()) {
            Note note = noteOptional.get();
            noteMapper.updateNoteFromDto(newNoteDto, note);
            noteRepository.save(note);
            return new NoteResponseDTO(note);
        }
        return null;
    }

    public NoteResponseDTO deleteNote(UUID notebookId, UUID noteId) {
        Optional<Notebook> notebookOptional = notebookRepository.findById(notebookId);
        Optional<Note> noteOptional = noteRepository.findById(noteId);
        if (notebookOptional.isPresent() && noteOptional.isPresent()) {
            noteRepository.deleteById(noteOptional.get().getId());
            return new NoteResponseDTO(noteOptional.get());
        }
        return null;
    }
}
