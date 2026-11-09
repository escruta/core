package com.escruta.core.repositories;

import com.escruta.core.entities.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends CrudRepository<Conversation, String> {
    List<Conversation> findByNotebookIdOrderByUpdatedAtDesc(UUID notebookId, Pageable pageable);

    List<Conversation> findByNotebookIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(
            UUID notebookId,
            String title,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"notebook"})
    Optional<Conversation> findByIdAndNotebookId(String id, UUID notebookId);

    long countByNotebookId(UUID notebookId);

    long countByNotebookIdAndTitleContainingIgnoreCase(UUID notebookId, String title);
}
