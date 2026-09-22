package com.escruta.core.services;

import com.escruta.core.dtos.sourcegroup.SourceGroupCreationDTO;
import com.escruta.core.dtos.sourcegroup.SourceGroupResponseDTO;
import com.escruta.core.dtos.sourcegroup.SourceGroupUpdateDTO;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.SourceGroup;
import com.escruta.core.mappers.SourceGroupMapper;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.SourceGroupRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SourceGroupService {

    private final SourceGroupRepository sourceGroupRepository;
    private final NotebookRepository notebookRepository;
    private final SourceGroupMapper sourceGroupMapper;

    @Transactional(readOnly = true)
    public List<SourceGroupResponseDTO> getGroups(UUID notebookId) {
        return sourceGroupRepository
                .findByNotebookIdOrderByCreatedAtDesc(notebookId)
                .stream()
                .map(sourceGroupMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public SourceGroupResponseDTO createGroup(UUID notebookId, SourceGroupCreationDTO creationDTO) {
        Notebook notebook = notebookRepository
                .findById(notebookId)
                .orElseThrow(() -> new EntityNotFoundException("Notebook not found"));

        SourceGroup group = new SourceGroup();
        group.setNotebook(notebook);
        group.setTitle(creationDTO.title());
        group.setColor(creationDTO.color());

        group = sourceGroupRepository.save(group);
        notebookRepository.touchLastActivity(notebookId);
        return sourceGroupMapper.toResponseDTO(group);
    }

    @Transactional
    public Optional<SourceGroupResponseDTO> updateGroup(UUID notebookId, UUID groupId, SourceGroupUpdateDTO updateDTO) {
        return sourceGroupRepository.findByIdAndNotebookId(groupId, notebookId).map(group -> {
            group.setTitle(updateDTO.title());
            group.setColor(updateDTO.color());
            notebookRepository.touchLastActivity(notebookId);
            return sourceGroupMapper.toResponseDTO(sourceGroupRepository.save(group));
        });
    }

    @Transactional
    public boolean deleteGroup(UUID notebookId, UUID groupId) {
        return sourceGroupRepository.findByIdAndNotebookId(groupId, notebookId).map(group -> {
            sourceGroupRepository.delete(group);
            notebookRepository.touchLastActivity(notebookId);
            return true;
        }).orElse(false);
    }
}
