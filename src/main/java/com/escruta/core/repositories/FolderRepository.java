package com.escruta.core.repositories;

import com.escruta.core.entities.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FolderRepository extends JpaRepository<Folder, UUID> {
    List<Folder> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Folder> findByIdAndUserId(UUID id, UUID userId);
}
