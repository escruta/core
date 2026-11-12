package com.escruta.core.repositories;

import com.escruta.core.entities.NoteGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoteGroupRepository extends JpaRepository<NoteGroup, UUID> {
    List<NoteGroup> findByNotebookIdOrderByCreatedAtDesc(UUID notebookId);

    Optional<NoteGroup> findByIdAndNotebookId(UUID id, UUID notebookId);
}
