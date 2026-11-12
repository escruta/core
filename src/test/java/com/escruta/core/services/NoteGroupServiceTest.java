package com.escruta.core.services;

import com.escruta.core.dtos.notegroup.NoteGroupCreationDTO;
import com.escruta.core.dtos.notegroup.NoteGroupResponseDTO;
import com.escruta.core.dtos.notegroup.NoteGroupUpdateDTO;
import com.escruta.core.entities.NoteGroup;
import com.escruta.core.entities.Notebook;
import com.escruta.core.mappers.NoteGroupMapper;
import com.escruta.core.repositories.NoteGroupRepository;
import com.escruta.core.repositories.NotebookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NoteGroupService Tests")
class NoteGroupServiceTest {
    @Mock
    private NoteGroupRepository noteGroupRepository;

    @Mock
    private NotebookRepository notebookRepository;

    @Mock
    private NoteGroupMapper noteGroupMapper;

    @InjectMocks
    private NoteGroupService noteGroupService;

    private static final UUID NOTEBOOK_ID = UUID.randomUUID();
    private static final UUID GROUP_ID = UUID.randomUUID();

    @Test
    @DisplayName("Should return note groups for notebook")
    void getGroups_shouldReturnGroups() {
        NoteGroup group = createGroup();
        NoteGroupResponseDTO response = new NoteGroupResponseDTO(group);

        when(noteGroupRepository.findByNotebookIdOrderByCreatedAtDesc(NOTEBOOK_ID)).thenReturn(List.of(group));
        when(noteGroupMapper.toResponseDTO(group)).thenReturn(response);

        List<NoteGroupResponseDTO> result = noteGroupService.getGroups(NOTEBOOK_ID);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("Test Group");
    }

    @Test
    @DisplayName("Should create a note group")
    void createGroup_shouldCreateGroup() {
        Notebook notebook = createNotebook();
        NoteGroup group = createGroup();
        NoteGroupResponseDTO response = new NoteGroupResponseDTO(group);
        NoteGroupCreationDTO dto = new NoteGroupCreationDTO("Test Group", "blue");

        when(notebookRepository.getReferenceById(NOTEBOOK_ID)).thenReturn(notebook);
        when(noteGroupRepository.save(any(NoteGroup.class))).thenReturn(group);
        when(noteGroupMapper.toResponseDTO(group)).thenReturn(response);

        NoteGroupResponseDTO result = noteGroupService.createGroup(NOTEBOOK_ID, dto);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Test Group");
        verify(noteGroupRepository).save(any(NoteGroup.class));
        verify(notebookRepository).touchLastActivity(NOTEBOOK_ID);
    }

    @Test
    @DisplayName("Should update a note group")
    void updateGroup_shouldUpdateGroup() {
        NoteGroup group = createGroup();
        NoteGroupUpdateDTO dto = new NoteGroupUpdateDTO("Renamed Group", "red");

        when(noteGroupRepository.findByIdAndNotebookId(GROUP_ID, NOTEBOOK_ID)).thenReturn(Optional.of(group));
        when(noteGroupRepository.save(group)).thenReturn(group);
        when(noteGroupMapper.toResponseDTO(group)).thenReturn(new NoteGroupResponseDTO(group));

        Optional<NoteGroupResponseDTO> result = noteGroupService.updateGroup(NOTEBOOK_ID, GROUP_ID, dto);

        assertThat(result).isPresent();
        assertThat(group.getTitle()).isEqualTo("Renamed Group");
        assertThat(group.getColor()).isEqualTo("red");
        verify(notebookRepository).touchLastActivity(NOTEBOOK_ID);
    }

    @Test
    @DisplayName("Should return empty when updating a missing note group")
    void updateGroup_shouldReturnEmptyWhenNotFound() {
        when(noteGroupRepository.findByIdAndNotebookId(GROUP_ID, NOTEBOOK_ID)).thenReturn(Optional.empty());

        Optional<NoteGroupResponseDTO> result = noteGroupService.updateGroup(
                NOTEBOOK_ID,
                GROUP_ID,
                new NoteGroupUpdateDTO("Renamed Group", null)
        );

        assertThat(result).isEmpty();
        verify(noteGroupRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should delete a note group")
    void deleteGroup_shouldDeleteGroup() {
        NoteGroup group = createGroup();
        when(noteGroupRepository.findByIdAndNotebookId(GROUP_ID, NOTEBOOK_ID)).thenReturn(Optional.of(group));

        boolean result = noteGroupService.deleteGroup(NOTEBOOK_ID, GROUP_ID);

        assertThat(result).isTrue();
        verify(noteGroupRepository).delete(group);
        verify(notebookRepository).touchLastActivity(NOTEBOOK_ID);
    }

    @Test
    @DisplayName("Should return false when deleting a missing note group")
    void deleteGroup_shouldReturnFalseWhenNotFound() {
        when(noteGroupRepository.findByIdAndNotebookId(GROUP_ID, NOTEBOOK_ID)).thenReturn(Optional.empty());

        boolean result = noteGroupService.deleteGroup(NOTEBOOK_ID, GROUP_ID);

        assertThat(result).isFalse();
        verify(noteGroupRepository, never()).delete(any());
    }

    private NoteGroup createGroup() {
        NoteGroup group = new NoteGroup();
        group.setId(GROUP_ID);
        group.setNotebook(createNotebook());
        group.setTitle("Test Group");
        return group;
    }

    private Notebook createNotebook() {
        Notebook notebook = new Notebook();
        notebook.setId(NOTEBOOK_ID);
        notebook.setTitle("Test Notebook");
        return notebook;
    }
}
