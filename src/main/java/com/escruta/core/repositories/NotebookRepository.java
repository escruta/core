package com.escruta.core.repositories;

import com.escruta.core.entities.Notebook;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotebookRepository extends JpaRepository<Notebook, UUID> {
    List<Notebook> findByUserId(UUID userId);

    List<Notebook> findByUserId(UUID userId, Pageable pageable);

    List<Notebook> findByUserIdAndTitleContainingIgnoreCase(UUID userId, String title, Pageable pageable);

    long countByUserId(UUID userId);

    long countByUserIdAndTitleContainingIgnoreCase(UUID userId, String title);

    boolean existsByIdAndUserId(UUID notebookId, UUID userId);

    @Query("select n.user.id from Notebook n where n.id = :notebookId")
    UUID findOwnerId(UUID notebookId);

    @Transactional
    @Modifying
    @Query("UPDATE Notebook n SET n.summary = :summary WHERE n.id = :notebookId")
    void updateSummary(UUID notebookId, String summary);

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    @Modifying
    @Query(value = "UPDATE notebooks SET last_activity_at = CURRENT_TIMESTAMP WHERE id = :notebookId", nativeQuery = true)
    void touchLastActivity(UUID notebookId);
}
