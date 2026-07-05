package com.escruta.core.services;

import com.escruta.core.dtos.notebook.NotebookCreationDTO;
import com.escruta.core.dtos.notebook.NotebookResponseDTO;
import com.escruta.core.dtos.notebook.NotebookUpdateDTO;
import com.escruta.core.dtos.notebook.NotebookWithDetailsDTO;
import com.escruta.core.dtos.notebook.NotebooksPageResponse;
import com.escruta.core.entities.Notebook;
import com.escruta.core.mappers.NotebookMapper;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.FolderRepository;
import com.escruta.core.repositories.SourceRepository;
import lombok.RequiredArgsConstructor;
import com.escruta.core.events.NotebookDeletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotebookService {
    private final NotebookRepository notebookRepository;
    private final UserService userService;
    private final SourceRepository sourceRepository;
    private final FolderRepository folderRepository;
    private final NotebookMapper notebookMapper;
    private final NoteService noteService;
    private final ApplicationEventPublisher eventPublisher;

    public List<NotebookResponseDTO> getAllUserNotebooks() {
        return notebookRepository.findByUserId(userService.getUserId()).stream().map(NotebookResponseDTO::new).toList();
    }

    public Optional<NotebookWithDetailsDTO> getUserNotebookWithDetails(UUID id) {
        Optional<Notebook> notebookOptional = notebookRepository.findById(id);
        if (notebookOptional.isPresent()) {
            Notebook notebook = notebookOptional.get();
            var notes = noteService.getNotes(id);
            var sources = sourceRepository.findByNotebookId(id);
            return Optional.of(new NotebookWithDetailsDTO(notebook, notes, sources));
        }

        return Optional.empty();
    }

    public NotebookResponseDTO createNotebook(NotebookCreationDTO createNotebookDto) {
        var currentUser = userService.getCurrentUser();
        if (currentUser != null) {
            Notebook notebook = notebookMapper.toNotebook(createNotebookDto, currentUser);
            if (createNotebookDto.folderId() != null) {
                notebook.setFolder(folderRepository.findById(createNotebookDto.folderId()).orElse(null));
            }
            notebookRepository.save(notebook);
            return new NotebookResponseDTO(notebook);
        }
        return null;
    }

    public NotebookResponseDTO updateNotebook(NotebookUpdateDTO newNotebookDto) {
        try {
            UUID notebookId = UUID.fromString(newNotebookDto.id());
            Optional<Notebook> notebookOptional = notebookRepository.findById(notebookId);
            if (notebookOptional.isPresent()) {
                Notebook notebook = notebookOptional.get();
                notebookMapper.updateNotebookFromDto(newNotebookDto, notebook);
                if (Boolean.TRUE.equals(newNotebookDto.removeFolder())) {
                    notebook.setFolder(null);
                } else if (newNotebookDto.folderId() != null) {
                    notebook.setFolder(folderRepository
                            .findById(newNotebookDto.folderId())
                            .orElse(notebook.getFolder()));
                }
                notebookRepository.save(notebook);
                return new NotebookResponseDTO(notebook);
            }
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public NotebooksPageResponse getUserNotebooks(int limit, int offset, String search, String sort) {
        var currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return new NotebooksPageResponse(List.of(), 0, false);
        }

        var userId = currentUser.getId();
        var pageable = PageRequest.of(offset / limit, limit, buildSort(sort));

        List<Notebook> notebooks;
        long total;

        if (search != null && !search.isBlank()) {
            notebooks = notebookRepository.findByUserIdAndTitleContainingIgnoreCase(userId, search.trim(), pageable);
            total = notebookRepository.countByUserIdAndTitleContainingIgnoreCase(userId, search.trim());
        } else {
            notebooks = notebookRepository.findByUserId(userId, pageable);
            total = notebookRepository.countByUserId(userId);
        }

        var list = notebooks.stream().map(NotebookResponseDTO::new).toList();
        boolean hasMore = (offset + list.size()) < total;
        return new NotebooksPageResponse(list, total, hasMore);
    }

    private Sort buildSort(String sort) {
        return switch (sort) {
            case "Oldest" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "Alphabetical" -> Sort.by(Sort.Direction.ASC, "title");
            case "Reverse Alphabetical" -> Sort.by(Sort.Direction.DESC, "title");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    @Transactional
    public NotebookResponseDTO deleteNotebook(NotebookUpdateDTO notebookDto) {
        try {
            UUID notebookId = UUID.fromString(notebookDto.id());
            Optional<Notebook> notebookOptional = notebookRepository.findById(notebookId);
            if (notebookOptional.isPresent()) {
                Notebook notebook = notebookOptional.get();
                notebookRepository.deleteById(notebook.getId());
                eventPublisher.publishEvent(new NotebookDeletedEvent(this, notebook.getId()));
                return new NotebookResponseDTO(notebook);
            }
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
