package com.escruta.core.mappers;

import com.escruta.core.dtos.folder.FolderResponseDTO;
import com.escruta.core.entities.Folder;
import org.springframework.stereotype.Component;

@Component
public class FolderMapper {
    public FolderResponseDTO toResponseDTO(Folder folder) {
        if (folder == null) return null;
        
        return new FolderResponseDTO(
                folder.getId(),
                folder.getUser() != null ? folder.getUser().getId() : null,
                folder.getTitle(),
                folder.getColor(),
                folder.getCreatedAt(),
                folder.getUpdatedAt()
        );
    }
}
