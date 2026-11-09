package com.escruta.core.services;

import com.escruta.core.dtos.folder.FolderCreationDTO;
import com.escruta.core.dtos.folder.FolderResponseDTO;
import com.escruta.core.dtos.folder.FolderUpdateDTO;
import com.escruta.core.entities.Folder;
import com.escruta.core.entities.User;
import com.escruta.core.mappers.FolderMapper;
import com.escruta.core.repositories.FolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final FolderMapper folderMapper;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<FolderResponseDTO> getFoldersForCurrentUser() {
        UUID userId = requireUserId();
        return folderRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(folderMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public FolderResponseDTO createFolder(FolderCreationDTO creationDTO) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            throw new org.springframework.security.authentication.BadCredentialsException("User not authenticated");
        }
        Folder folder = new Folder();
        folder.setTitle(creationDTO.title());
        folder.setColor(creationDTO.color());
        folder.setUser(currentUser);

        folder = folderRepository.save(folder);
        return folderMapper.toResponseDTO(folder);
    }

    @Transactional
    public Optional<FolderResponseDTO> updateFolder(UUID id, FolderUpdateDTO updateDTO) {
        UUID userId = requireUserId();
        return folderRepository.findByIdAndUserId(id, userId).map(folder -> {
            folder.setTitle(updateDTO.title());
            folder.setColor(updateDTO.color());
            return folderMapper.toResponseDTO(folderRepository.save(folder));
        });
    }

    @Transactional
    public boolean deleteFolder(UUID id) {
        UUID userId = requireUserId();
        return folderRepository.findByIdAndUserId(id, userId).map(folder -> {
            folderRepository.delete(folder);
            return true;
        }).orElse(false);
    }

    private UUID requireUserId() {
        UUID userId = userService.getUserId();
        if (userId == null) {
            throw new org.springframework.security.authentication.BadCredentialsException("User not authenticated");
        }
        return userId;
    }
}
