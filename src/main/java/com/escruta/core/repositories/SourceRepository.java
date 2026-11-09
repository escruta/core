package com.escruta.core.repositories;

import com.escruta.core.dtos.source.SourceResponseDTO;
import com.escruta.core.entities.Source;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SourceRepository extends JpaRepository<Source, UUID> {
    Optional<Source> findByIdAndNotebookId(UUID id, UUID notebookId);

    boolean existsByNotebookId(UUID notebookId);

    @Query("select s.link from Source s where s.notebook.id = :notebookId and s.link is not null")
    List<String> findLinksByNotebookId(UUID notebookId);

    @Query("select s.status from Source s where s.id = :sourceId")
    Optional<com.escruta.core.entities.enums.SourceStatus> findStatusById(UUID sourceId);

    @Query("select s.summary from Source s where s.id = :sourceId and s.notebook.id = :notebookId")
    Optional<String> findSummaryTextByIdAndNotebookId(UUID sourceId, UUID notebookId);

    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Source s set s.status = :status, s.updatedAt = CURRENT_TIMESTAMP where s.id = :sourceId")
    void updateStatus(UUID sourceId, com.escruta.core.entities.enums.SourceStatus status);

    @Query("select s.notebook.user.id from Source s where s.id = :sourceId")
    UUID findNotebookOwnerId(UUID sourceId);

    @Query("""
            select new com.escruta.core.dtos.source.SourceResponseDTO(
                s.id, s.notebook.id, g.id, s.icon, s.title, s.isConvertedByAi,
                s.link, s.status, s.type, s.createdAt, s.updatedAt)
            from Source s
            left join s.sourceGroup g
            where s.notebook.id = :notebookId
            order by s.createdAt desc
            """)
    List<SourceResponseDTO> findSummariesByNotebookId(UUID notebookId);

    @Query("""
            select new com.escruta.core.dtos.source.SourceResponseDTO(
                s.id, s.notebook.id, g.id, s.icon, s.title, s.isConvertedByAi,
                s.link, s.status, s.type, s.createdAt, s.updatedAt)
            from Source s
            left join s.sourceGroup g
            where s.notebook.id = :notebookId and g.id = :groupId
            order by s.createdAt desc
            """)
    List<SourceResponseDTO> findSummariesByNotebookIdAndSourceGroupId(UUID notebookId, UUID groupId);

    @Query("""
            select new com.escruta.core.dtos.source.SourceResponseDTO(
                s.id, s.notebook.id, g.id, s.icon, s.title, s.isConvertedByAi,
                s.link, s.status, s.type, s.createdAt, s.updatedAt)
            from Source s
            left join s.sourceGroup g
            where s.id = :sourceId and s.notebook.id = :notebookId
            """)
    Optional<SourceResponseDTO> findResponseByIdAndNotebookId(UUID sourceId, UUID notebookId);

    boolean existsByIdAndNotebookId(UUID sourceId, UUID notebookId);

    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Source s set s.summary = null, s.updatedAt = CURRENT_TIMESTAMP where s.id = :sourceId")
    void clearSummary(UUID sourceId);

    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Source s
            set s.summary = null, s.updatedAt = CURRENT_TIMESTAMP
            where s.id = :sourceId and s.notebook.id = :notebookId
            """)
    int clearSummaryByIdAndNotebookId(UUID sourceId, UUID notebookId);
}
