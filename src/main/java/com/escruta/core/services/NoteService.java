package com.escruta.core.services;

import com.escruta.core.dtos.note.NoteCreationDTO;
import com.escruta.core.dtos.note.NoteResponseDTO;
import com.escruta.core.dtos.note.NoteUpdateDTO;
import com.escruta.core.dtos.note.NoteWithContentDTO;
import com.escruta.core.entities.Note;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.Folder;
import com.escruta.core.mappers.NoteMapper;
import com.escruta.core.repositories.NoteRepository;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.FolderRepository;
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
    private final FolderRepository folderRepository;
    private final UserService userService;
    private final NoteMapper noteMapper;

    public List<NoteResponseDTO> getNotes(UUID notebookId) {
        var currentUser = userService.getCurrentUser();
        if (currentUser == null)
            return List.of();

        if (notebookId != null) {
            return noteRepository
                    .findByNotebookIdAndUserId(notebookId, currentUser.getId())
                    .stream()
                    .map(NoteResponseDTO::new)
                    .toList();
        }
        return noteRepository.findByUserId(currentUser.getId()).stream().map(NoteResponseDTO::new).toList();
    }

    public NoteWithContentDTO getNote(UUID noteId) {
        Optional<Note> note = noteRepository.findById(noteId);
        return note.map(NoteWithContentDTO::new).orElse(null);
    }

    public NoteResponseDTO addNote(NoteCreationDTO newNoteDto) {
        var currentUser = userService.getCurrentUser();
        if (currentUser == null)
            return null;

        Notebook notebook = null;
        if (newNoteDto.notebookId() != null) {
            Optional<Notebook> notebookOptional = notebookRepository.findById(newNoteDto.notebookId());
            if (notebookOptional.isPresent()) {
                notebook = notebookOptional.get();
            } else {
                return null;
            }
        }
        
        Folder folder = null;
        if (newNoteDto.folderId() != null) {
            folder = folderRepository.findById(newNoteDto.folderId()).orElse(null);
        }

        Note note = noteMapper.toNote(newNoteDto, notebook, currentUser);
        note.setFolder(folder);
        noteRepository.save(note);
        return new NoteResponseDTO(note);
    }

    public NoteResponseDTO updateNote(NoteUpdateDTO newNoteDto) {
        Optional<Note> noteOptional = noteRepository.findById(UUID.fromString(newNoteDto.id()));
        if (noteOptional.isPresent()) {
            Note note = noteOptional.get();
            noteMapper.updateNoteFromDto(newNoteDto, note);
            if (Boolean.TRUE.equals(newNoteDto.removeFolder())) {
                note.setFolder(null);
            } else if (newNoteDto.folderId() != null) {
                note.setFolder(folderRepository.findById(newNoteDto.folderId()).orElse(note.getFolder()));
            }
            noteRepository.save(note);
            return new NoteResponseDTO(note);
        }
        return null;
    }

    public NoteResponseDTO deleteNote(UUID noteId) {
        Optional<Note> noteOptional = noteRepository.findById(noteId);
        if (noteOptional.isPresent()) {
            noteRepository.deleteById(noteId);
            return new NoteResponseDTO(noteOptional.get());
        }
        return null;
    }
}
