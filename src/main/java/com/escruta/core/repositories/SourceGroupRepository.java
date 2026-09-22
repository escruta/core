package com.escruta.core.repositories;

import com.escruta.core.entities.SourceGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SourceGroupRepository extends JpaRepository<SourceGroup, UUID> {
    List<SourceGroup> findByNotebookIdOrderByCreatedAtDesc(UUID notebookId);

    Optional<SourceGroup> findByIdAndNotebookId(UUID id, UUID notebookId);
}
