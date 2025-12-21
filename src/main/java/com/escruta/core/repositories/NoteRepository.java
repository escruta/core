package com.escruta.core.repositories;

import com.escruta.core.entities.Note;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NoteRepository extends CrudRepository<Note, UUID> {
    List<Note> findByNotebookId(UUID notebookId);
}
