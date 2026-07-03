package com.escruta.core.services;

import com.escruta.core.dtos.note.NoteResponseDTO;
import com.escruta.core.dtos.notebook.NotebookCreationDTO;
import com.escruta.core.dtos.notebook.NotebookResponseDTO;
import com.escruta.core.dtos.notebook.NotebookUpdateDTO;
import com.escruta.core.dtos.notebook.NotebookWithDetailsDTO;
import com.escruta.core.dtos.source.SourceResponseDTO;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.User;
import com.escruta.core.mappers.NotebookMapper;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.SourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.springframework.context.ApplicationEventPublisher;
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
@DisplayName("NotebookService Tests")
class NotebookServiceTest {
    @Mock
    private NotebookRepository notebookRepository;

    @Mock
    private UserService userService;

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private NotebookMapper notebookMapper;

    @Mock
    private NoteService noteService;

    @InjectMocks
    private NotebookService notebookService;

    private static final UUID NOTEBOOK_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @DisplayName("Should return all notebooks for current user")
    void getAllUserNotebooks_shouldReturnAllUserNotebooks() {
        User user = createUser();
        Notebook notebook1 = createNotebook(UUID.randomUUID(), "Notebook 1", user);
        Notebook notebook2 = createNotebook(UUID.randomUUID(), "Notebook 2", user);

        when(userService.getUserId()).thenReturn(USER_ID);
        when(notebookRepository.findByUserId(USER_ID)).thenReturn(Arrays.asList(notebook1, notebook2));

        List<NotebookResponseDTO> result = notebookService.getAllUserNotebooks();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).title()).isEqualTo("Notebook 1");
        assertThat(result.get(1).title()).isEqualTo("Notebook 2");
    }

    @Test
    @DisplayName("Should return empty list when user has no notebooks")
    void getAllUserNotebooks_shouldReturnEmptyListWhenNoNotebooks() {
        when(userService.getUserId()).thenReturn(USER_ID);
        when(notebookRepository.findByUserId(USER_ID)).thenReturn(List.of());

        List<NotebookResponseDTO> result = notebookService.getAllUserNotebooks();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return notebook with details when found")
    void getUserNotebookWithDetails_shouldReturnNotebookWithDetails() {
        User user = createUser();
        Notebook notebook = createNotebook(NOTEBOOK_ID, "Test Notebook", user);
        List<NoteResponseDTO> notes = List.of();
        List<SourceResponseDTO> sources = List.of();

        when(notebookRepository.findById(NOTEBOOK_ID)).thenReturn(Optional.of(notebook));
        when(noteService.getNotes(NOTEBOOK_ID)).thenReturn(notes);
        when(sourceRepository.findByNotebookId(NOTEBOOK_ID)).thenReturn(sources);

        Optional<NotebookWithDetailsDTO> result = notebookService.getUserNotebookWithDetails(NOTEBOOK_ID);

        assertThat(result).isPresent();
        assertThat(result.get().title()).isEqualTo("Test Notebook");
        assertThat(result.get().notes()).isEqualTo(notes);
        assertThat(result.get().sources()).isEmpty();
    }

    @Test
    @DisplayName("Should return empty optional when notebook not found")
    void getUserNotebookWithDetails_shouldReturnEmptyWhenNotFound() {
        when(notebookRepository.findById(NOTEBOOK_ID)).thenReturn(Optional.empty());

        Optional<NotebookWithDetailsDTO> result = notebookService.getUserNotebookWithDetails(NOTEBOOK_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should create notebook successfully")
    void createNotebook_shouldCreateNotebook() {
        User user = createUser();
        NotebookCreationDTO dto = new NotebookCreationDTO("📓", "New Notebook", null);
        Notebook notebook = createNotebook(NOTEBOOK_ID, "New Notebook", user);

        when(userService.getCurrentUser()).thenReturn(user);
        when(notebookMapper.toNotebook(dto, user)).thenReturn(notebook);
        when(notebookRepository.save(notebook)).thenReturn(notebook);

        NotebookResponseDTO result = notebookService.createNotebook(dto);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("New Notebook");
        verify(notebookRepository).save(notebook);
    }

    @Test
    @DisplayName("Should return null when user not authenticated during creation")
    void createNotebook_shouldReturnNullWhenUserNotAuthenticated() {
        NotebookCreationDTO dto = new NotebookCreationDTO("📓", "New Notebook", null);

        when(userService.getCurrentUser()).thenReturn(null);

        NotebookResponseDTO result = notebookService.createNotebook(dto);

        assertThat(result).isNull();
        verify(notebookRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update notebook successfully")
    void updateNotebook_shouldUpdateNotebook() {
        User user = createUser();
        Notebook existingNotebook = createNotebook(NOTEBOOK_ID, "Old Title", user);
        NotebookUpdateDTO dto = new NotebookUpdateDTO(NOTEBOOK_ID.toString(), "📒", "Updated Title", null, null);

        when(notebookRepository.findById(NOTEBOOK_ID)).thenReturn(Optional.of(existingNotebook));
        doAnswer(invocation -> {
            NotebookUpdateDTO updateDto = invocation.getArgument(0);
            Notebook notebook = invocation.getArgument(1);
            notebook.setIcon(updateDto.icon());
            notebook.setTitle(updateDto.title());
            return null;
        }).when(notebookMapper).updateNotebookFromDto(any(), any());

        NotebookResponseDTO result = notebookService.updateNotebook(dto);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Updated Title");
        verify(notebookMapper).updateNotebookFromDto(dto, existingNotebook);
        verify(notebookRepository).save(existingNotebook);
    }

    @Test
    @DisplayName("Should return null when updating non-existent notebook")
    void updateNotebook_shouldReturnNullWhenNotFound() {
        NotebookUpdateDTO dto = new NotebookUpdateDTO(NOTEBOOK_ID.toString(), "📒", "Updated Title", null, null);

        when(notebookRepository.findById(NOTEBOOK_ID)).thenReturn(Optional.empty());

        NotebookResponseDTO result = notebookService.updateNotebook(dto);

        assertThat(result).isNull();
        verify(notebookRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return null when notebook ID is invalid")
    void updateNotebook_shouldReturnNullWhenInvalidId() {
        NotebookUpdateDTO dto = new NotebookUpdateDTO("invalid-uuid", "📒", "Updated Title", null, null);

        NotebookResponseDTO result = notebookService.updateNotebook(dto);

        assertThat(result).isNull();
        verify(notebookRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Should delete notebook successfully")
    void deleteNotebook_shouldDeleteNotebook() {
        User user = createUser();
        Notebook notebook = createNotebook(NOTEBOOK_ID, "Notebook to Delete", user);
        NotebookUpdateDTO dto = new NotebookUpdateDTO(NOTEBOOK_ID.toString(), null, null, null, null);

        when(notebookRepository.findById(NOTEBOOK_ID)).thenReturn(Optional.of(notebook));

        NotebookResponseDTO result = notebookService.deleteNotebook(dto);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Notebook to Delete");
        verify(notebookRepository).deleteById(NOTEBOOK_ID);
        verify(eventPublisher).publishEvent(any(com.escruta.core.events.NotebookDeletedEvent.class));
    }

    @Test
    @DisplayName("Should return null when deleting non-existent notebook")
    void deleteNotebook_shouldReturnNullWhenNotFound() {
        NotebookUpdateDTO dto = new NotebookUpdateDTO(NOTEBOOK_ID.toString(), null, null, null, null);

        when(notebookRepository.findById(NOTEBOOK_ID)).thenReturn(Optional.empty());

        NotebookResponseDTO result = notebookService.deleteNotebook(dto);

        assertThat(result).isNull();
        verify(notebookRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Should return null when deleting with invalid notebook ID")
    void deleteNotebook_shouldReturnNullWhenInvalidId() {
        NotebookUpdateDTO dto = new NotebookUpdateDTO("invalid-uuid", null, null, null, null);

        NotebookResponseDTO result = notebookService.deleteNotebook(dto);

        assertThat(result).isNull();
        verify(notebookRepository, never()).findById(any());
    }

    private Notebook createNotebook(UUID id, String title, User user) {
        Notebook notebook = new Notebook();
        notebook.setId(id);
        notebook.setTitle(title);
        notebook.setUser(user);
        notebook.setIcon("📓");
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
