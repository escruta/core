package com.escruta.core.services;

import com.escruta.core.dtos.note.NoteCreationDTO;
import com.escruta.core.dtos.note.NoteResponseDTO;
import com.escruta.core.dtos.note.NoteUpdateDTO;
import com.escruta.core.dtos.note.NoteWithContentDTO;
import com.escruta.core.entities.Note;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.User;
import com.escruta.core.mappers.NoteMapper;
import com.escruta.core.repositories.NoteRepository;
import com.escruta.core.repositories.NotebookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NoteService Tests")
class NoteServiceTest {
    @Mock
    private NoteRepository noteRepository;

    @Mock
    private NotebookRepository notebookRepository;

    @Mock
    private UserService userService;

    @Mock
    private NoteMapper noteMapper;

    @InjectMocks
    private NoteService noteService;

    private static final UUID NOTEBOOK_ID = UUID.randomUUID();
    private static final UUID NOTE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @DisplayName("Should return list of notes for notebook")
    void getNotes_shouldReturnListOfNotes() {
        User user = createUser();
        Notebook notebook = createNotebook();
        Note note1 = createNote(NOTE_ID, "Note 1", "Content 1", notebook);
        Note note2 = createNote(UUID.randomUUID(), "Note 2", "Content 2", notebook);

        when(userService.getCurrentUser()).thenReturn(user);
        when(noteRepository.findByNotebookIdAndNotebook_UserId(NOTEBOOK_ID, user.getId())).thenReturn(Arrays.asList(
                note1,
                note2
        ));

        List<NoteResponseDTO> result = noteService.getNotes(NOTEBOOK_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).title()).isEqualTo("Note 1");
        assertThat(result.get(1).title()).isEqualTo("Note 2");
    }

    @Test
    @DisplayName("Should return all notes for the user's notebooks when notebookId is null")
    void getNotes_shouldReturnListOfAllUserNotesWhenNotebookIdIsNull() {
        User user = createUser();
        Notebook notebook = createNotebook();
        Note note1 = createNote(NOTE_ID, "Note 1", "Content 1", notebook);
        Note note2 = createNote(UUID.randomUUID(), "Note 2", "Content 2", notebook);

        when(userService.getCurrentUser()).thenReturn(user);
        when(noteRepository.findByNotebook_UserId(user.getId())).thenReturn(Arrays.asList(note1, note2));

        List<NoteResponseDTO> result = noteService.getNotes(null);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("Should return empty list when no notes")
    void getNotes_shouldReturnEmptyListWhenNoNotes() {
        User user = createUser();
        when(userService.getCurrentUser()).thenReturn(user);
        when(noteRepository.findByNotebookIdAndNotebook_UserId(NOTEBOOK_ID, USER_ID)).thenReturn(List.of());

        List<NoteResponseDTO> result = noteService.getNotes(NOTEBOOK_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list when user is not authenticated")
    void getNotes_shouldReturnEmptyWhenUserNotAuthenticated() {
        when(userService.getCurrentUser()).thenReturn(null);

        List<NoteResponseDTO> result = noteService.getNotes(NOTEBOOK_ID);

        assertThat(result).isEmpty();
        verify(noteRepository, never()).findByNotebookIdAndNotebook_UserId(any(), any());
    }

    @Test
    @DisplayName("Should return note with content when found")
    void getNote_shouldReturnNoteWithContent() {
        Notebook notebook = createNotebook();
        Note note = createNote(NOTE_ID, "Test Note", "Test Content", notebook);

        when(noteRepository.findById(NOTE_ID)).thenReturn(Optional.of(note));

        NoteWithContentDTO result = noteService.getNote(NOTE_ID);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Test Note");
        assertThat(result.content()).isEqualTo("Test Content");
        assertThat(result.notebookId()).isEqualTo(NOTEBOOK_ID);
    }

    @Test
    @DisplayName("Should return null when note not found")
    void getNote_shouldReturnNullWhenNoteNotFound() {
        when(noteRepository.findById(NOTE_ID)).thenReturn(Optional.empty());

        NoteWithContentDTO result = noteService.getNote(NOTE_ID);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should add note to notebook successfully")
    void addNote_shouldAddNoteToNotebookSuccessfully() {
        NoteCreationDTO dto = new NoteCreationDTO(NOTEBOOK_ID, "New Note", "New Content");
        Notebook notebook = createNotebook();
        Note note = createNote(NOTE_ID, "New Note", "New Content", notebook);

        when(notebookRepository.findById(NOTEBOOK_ID)).thenReturn(Optional.of(notebook));
        when(noteMapper.toNote(dto, notebook)).thenReturn(note);
        when(noteRepository.save(note)).thenReturn(note);

        NoteResponseDTO result = noteService.addNote(dto);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("New Note");
        verify(noteRepository).save(note);
    }

    @Test
    @DisplayName("Should return null when adding note without notebook")
    void addNote_shouldReturnNullWhenNoNotebook() {
        NoteCreationDTO dto = new NoteCreationDTO(null, "New Note", "New Content");

        NoteResponseDTO result = noteService.addNote(dto);

        assertThat(result).isNull();
        verify(notebookRepository, never()).findById(any());
        verify(noteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return null when adding note to non-existent notebook")
    void addNote_shouldReturnNullWhenNotebookNotFound() {
        NoteCreationDTO dto = new NoteCreationDTO(NOTEBOOK_ID, "New Note", "New Content");

        when(notebookRepository.findById(NOTEBOOK_ID)).thenReturn(Optional.empty());

        NoteResponseDTO result = noteService.addNote(dto);

        assertThat(result).isNull();
        verify(noteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update note successfully")
    void updateNote_shouldUpdateNoteSuccessfully() {
        String noteIdStr = NOTE_ID.toString();
        NoteUpdateDTO dto = new NoteUpdateDTO(noteIdStr, "Updated Note", "Updated Content");
        Notebook notebook = createNotebook();
        Note existingNote = createNote(NOTE_ID, "Old Note", "Old Content", notebook);

        when(noteRepository.findById(NOTE_ID)).thenReturn(Optional.of(existingNote));

        NoteResponseDTO result = noteService.updateNote(dto);

        assertThat(result).isNotNull();
        verify(noteMapper).updateNoteFromDto(dto, existingNote);
        verify(noteRepository).save(existingNote);
    }

    @Test
    @DisplayName("Should return null when updating non-existent note")
    void updateNote_shouldReturnNullWhenNoteNotFound() {
        String noteIdStr = NOTE_ID.toString();
        NoteUpdateDTO dto = new NoteUpdateDTO(noteIdStr, "Updated Note", "Updated Content");

        when(noteRepository.findById(NOTE_ID)).thenReturn(Optional.empty());

        NoteResponseDTO result = noteService.updateNote(dto);

        assertThat(result).isNull();
        verify(noteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should delete note successfully")
    void deleteNote_shouldDeleteNoteSuccessfully() {
        Notebook notebook = createNotebook();
        Note note = createNote(NOTE_ID, "Note to Delete", "Content", notebook);

        when(noteRepository.findById(NOTE_ID)).thenReturn(Optional.of(note));

        NoteResponseDTO result = noteService.deleteNote(NOTE_ID);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Note to Delete");
        verify(noteRepository).deleteById(NOTE_ID);
    }

    @Test
    @DisplayName("Should return null when deleting non-existent note")
    void deleteNote_shouldReturnNullWhenNoteNotFound() {
        when(noteRepository.findById(NOTE_ID)).thenReturn(Optional.empty());

        NoteResponseDTO result = noteService.deleteNote(NOTE_ID);

        assertThat(result).isNull();
        verify(noteRepository, never()).deleteById(any());
    }

    private Note createNote(UUID id, String title, String content, Notebook notebook) {
        Note note = new Note();
        note.setId(id);
        note.setTitle(title);
        note.setContent(content);
        note.setNotebook(notebook);
        return note;
    }

    private Notebook createNotebook() {
        Notebook notebook = new Notebook();
        notebook.setId(NOTEBOOK_ID);
        notebook.setTitle("Test Notebook");
        return notebook;
    }

    private User createUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("test@example.com");
        user.setName("Test User");
        return user;
    }
}
