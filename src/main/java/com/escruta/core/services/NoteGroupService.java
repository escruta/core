package com.escruta.core.services;

import com.escruta.core.dtos.notegroup.NoteGroupCreationDTO;
import com.escruta.core.dtos.notegroup.NoteGroupResponseDTO;
import com.escruta.core.dtos.notegroup.NoteGroupUpdateDTO;
import com.escruta.core.entities.NoteGroup;
import com.escruta.core.entities.Notebook;
import com.escruta.core.mappers.NoteGroupMapper;
import com.escruta.core.repositories.NoteGroupRepository;
import com.escruta.core.repositories.NotebookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteGroupService {

    private final NoteGroupRepository noteGroupRepository;
    private final NotebookRepository notebookRepository;
    private final NoteGroupMapper noteGroupMapper;

    @Transactional(readOnly = true)
    public List<NoteGroupResponseDTO> getGroups(UUID notebookId) {
        return noteGroupRepository
                .findByNotebookIdOrderByCreatedAtDesc(notebookId)
                .stream()
                .map(noteGroupMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public NoteGroupResponseDTO createGroup(UUID notebookId, NoteGroupCreationDTO creationDTO) {
        Notebook notebook = notebookRepository.getReferenceById(notebookId);

        NoteGroup group = new NoteGroup();
        group.setNotebook(notebook);
        group.setTitle(creationDTO.title());
        group.setColor(creationDTO.color());

        group = noteGroupRepository.save(group);
        notebookRepository.touchLastActivity(notebookId);
        return noteGroupMapper.toResponseDTO(group);
    }

    @Transactional
    public Optional<NoteGroupResponseDTO> updateGroup(UUID notebookId, UUID groupId, NoteGroupUpdateDTO updateDTO) {
        return noteGroupRepository.findByIdAndNotebookId(groupId, notebookId).map(group -> {
            group.setTitle(updateDTO.title());
            group.setColor(updateDTO.color());
            notebookRepository.touchLastActivity(notebookId);
            return noteGroupMapper.toResponseDTO(noteGroupRepository.save(group));
        });
    }

    @Transactional
    public boolean deleteGroup(UUID notebookId, UUID groupId) {
        return noteGroupRepository.findByIdAndNotebookId(groupId, notebookId).map(group -> {
            noteGroupRepository.delete(group);
            notebookRepository.touchLastActivity(notebookId);
            return true;
        }).orElse(false);
    }
}
