package com.escruta.core.repositories;

import com.escruta.core.entities.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NoteRepository extends JpaRepository<Note, UUID> {
    List<Note> findByNotebookIdAndNotebook_UserId(UUID notebookId, UUID userId);

    List<Note> findByNotebook_UserId(UUID userId);
}
