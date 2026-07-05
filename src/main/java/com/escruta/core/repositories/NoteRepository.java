package com.escruta.core.repositories;

import com.escruta.core.entities.Note;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NoteRepository extends JpaRepository<Note, UUID> {
    List<Note> findByNotebookIdAndUserId(UUID notebookId, UUID userId);

    List<Note> findByUserId(UUID userId);

    List<Note> findByNotebookIdAndUserId(UUID notebookId, UUID userId, Pageable pageable);

    List<Note> findByNotebookIdAndUserIdAndTitleContainingIgnoreCase(
            UUID notebookId,
            UUID userId,
            String title,
            Pageable pageable
    );

    List<Note> findByUserId(UUID userId, Pageable pageable);

    List<Note> findByUserIdAndTitleContainingIgnoreCase(UUID userId, String title, Pageable pageable);

    long countByUserId(UUID userId);

    long countByUserIdAndTitleContainingIgnoreCase(UUID userId, String title);

    long countByNotebookIdAndUserId(UUID notebookId, UUID userId);

    long countByNotebookIdAndUserIdAndTitleContainingIgnoreCase(UUID notebookId, UUID userId, String title);
}
