package com.escruta.core.services;

import com.escruta.core.dtos.notebook.NotebookCreationDTO;
import com.escruta.core.dtos.notebook.NotebookResponseDTO;
import com.escruta.core.dtos.notebook.NotebookUpdateDTO;
import com.escruta.core.dtos.notebook.NotebookWithDetailsDTO;
import com.escruta.core.dtos.notebook.NotebooksPageResponse;
import com.escruta.core.entities.Notebook;
import com.escruta.core.mappers.NotebookMapper;
import com.escruta.core.repositories.FolderRepository;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.OffsetPageRequest;
import com.escruta.core.repositories.SourceRepository;
import lombok.RequiredArgsConstructor;
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

    public List<NotebookResponseDTO> getAllUserNotebooks() {
        UUID userId = userService.getUserId();
        if (userId == null) {
            return List.of();
        }
        return notebookRepository.findResponsesByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Optional<NotebookWithDetailsDTO> getUserNotebookWithDetails(UUID id) {
        Optional<Notebook> notebookOptional = notebookRepository.findById(id);
        if (notebookOptional.isPresent()) {
            Notebook notebook = notebookOptional.get();
            var notes = noteService.getNotes(id);
            var sources = sourceRepository.findSummariesByNotebookId(id);
            return Optional.of(new NotebookWithDetailsDTO(notebook, notes, sources));
        }

        return Optional.empty();
    }

    @Transactional
    public NotebookResponseDTO createNotebook(NotebookCreationDTO createNotebookDto) {
        var currentUser = userService.getCurrentUser();
        if (currentUser != null) {
            Notebook notebook = notebookMapper.toNotebook(createNotebookDto, currentUser);
            if (createNotebookDto.folderId() != null) {
                notebook.setFolder(folderRepository
                        .findByIdAndUserId(createNotebookDto.folderId(), currentUser.getId())
                        .orElse(null));
            }
            notebookRepository.save(notebook);
            return new NotebookResponseDTO(notebook);
        }
        return null;
    }

    @Transactional
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
                    UUID userId = userService.getUserId();
                    notebook.setFolder(folderRepository
                            .findByIdAndUserId(newNotebookDto.folderId(), userId)
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
        UUID userId = userService.getUserId();
        if (userId == null) {
            return new NotebooksPageResponse(List.of(), 0, false);
        }

        int boundedLimit = Math.max(1, Math.min(limit, 100));
        long boundedOffset = Math.max(0, offset);
        var pageable = new OffsetPageRequest(boundedOffset, boundedLimit, buildSort(sort));

        List<NotebookResponseDTO> notebooks;
        long total;

        if (search != null && !search.isBlank()) {
            String term = search.trim();
            notebooks = notebookRepository.findResponsesByUserIdAndTitle(userId, term, pageable);
            total = notebookRepository.countByUserIdAndTitleContainingIgnoreCase(userId, term);
        } else {
            notebooks = notebookRepository.findResponsesByUserId(userId, pageable);
            total = notebookRepository.countByUserId(userId);
        }

        boolean hasMore = (boundedOffset + notebooks.size()) < total;
        return new NotebooksPageResponse(notebooks, total, hasMore);
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
                return new NotebookResponseDTO(notebook);
            }
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
