package com.escruta.core.repositories;

import com.escruta.core.dtos.notebook.NotebookResponseDTO;
import com.escruta.core.entities.Notebook;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotebookRepository extends JpaRepository<Notebook, UUID> {
    long countByUserId(UUID userId);

    long countByUserIdAndTitleContainingIgnoreCase(UUID userId, String title);

    boolean existsByIdAndUserId(UUID notebookId, UUID userId);

    java.util.Optional<Notebook> findByIdAndUserId(UUID notebookId, UUID userId);

    @Query("select n.user.id from Notebook n where n.id = :notebookId")
    UUID findOwnerId(UUID notebookId);

    @Transactional
    @Modifying
    @Query("""
            UPDATE Notebook n
            SET n.summary = :summary,
                n.summaryTopics = :summaryTopics,
                n.updatedAt = CURRENT_TIMESTAMP
            WHERE n.id = :notebookId
            """)
    void updateSummary(UUID notebookId, String summary, String summaryTopics);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE notebooks SET last_activity_at = CURRENT_TIMESTAMP
            WHERE id = :notebookId
              AND (last_activity_at IS NULL
                   OR last_activity_at < CURRENT_TIMESTAMP - INTERVAL 2 MINUTE)
            """, nativeQuery = true)
    int touchLastActivity(UUID notebookId);

    @Query("""
            select new com.escruta.core.dtos.notebook.NotebookResponseDTO(
                n.id,
                new com.escruta.core.dtos.BasicUser(u),
                n.icon, n.title, f.id, n.createdAt, n.updatedAt, n.lastActivityAt)
            from Notebook n
            join n.user u
            left join n.folder f
            where u.id = :userId
            """)
    List<NotebookResponseDTO> findResponsesByUserId(UUID userId, Pageable pageable);

    @Query("""
            select new com.escruta.core.dtos.notebook.NotebookResponseDTO(
                n.id,
                new com.escruta.core.dtos.BasicUser(u),
                n.icon, n.title, f.id, n.createdAt, n.updatedAt, n.lastActivityAt)
            from Notebook n
            join n.user u
            left join n.folder f
            where u.id = :userId
            """)
    List<NotebookResponseDTO> findResponsesByUserId(UUID userId);

    @Query("""
            select new com.escruta.core.dtos.notebook.NotebookResponseDTO(
                n.id,
                new com.escruta.core.dtos.BasicUser(u),
                n.icon, n.title, f.id, n.createdAt, n.updatedAt, n.lastActivityAt)
            from Notebook n
            join n.user u
            left join n.folder f
            where u.id = :userId
              and lower(n.title) like lower(concat('%', :title, '%'))
            """)
    List<NotebookResponseDTO> findResponsesByUserIdAndTitle(UUID userId, String title, Pageable pageable);
}
