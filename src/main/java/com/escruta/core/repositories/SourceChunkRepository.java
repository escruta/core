package com.escruta.core.repositories;

import com.escruta.core.entities.SourceChunk;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SourceChunkRepository extends JpaRepository<SourceChunk, UUID> {
    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SourceChunk c where c.sourceId = :sourceId")
    void deleteAllBySourceId(UUID sourceId);

    Optional<SourceChunk> findByIdAndNotebookId(UUID id, UUID notebookId);

    long countByNotebookIdAndSourceId(UUID notebookId, UUID sourceId);

    List<SourceChunk> findByNotebookIdOrderByChunkIndexAsc(UUID notebookId, Pageable pageable);

    List<SourceChunk> findByNotebookIdAndSourceIdInOrderByChunkIndexAsc(
            UUID notebookId,
            List<UUID> sourceIds,
            Pageable pageable
    );

    List<SourceChunk> findByNotebookIdAndSourceIdAndChunkIndexBetweenOrderByChunkIndexAsc(
            UUID notebookId,
            UUID sourceId,
            int startChunkIndex,
            int endChunkIndex
    );

    @Query("""
            SELECT c FROM SourceChunk c
            WHERE c.notebookId = :notebookId
              AND LOCATE(LOWER(:phrase), LOWER(c.content)) > 0
            ORDER BY c.sourceId ASC, c.chunkIndex ASC
            """)
    List<SourceChunk> findExactByNotebook(UUID notebookId, String phrase, Pageable pageable);

    @Query("""
            SELECT c FROM SourceChunk c
            WHERE c.notebookId = :notebookId
              AND c.sourceId IN (:sourceIds)
              AND LOCATE(LOWER(:phrase), LOWER(c.content)) > 0
            ORDER BY c.sourceId ASC, c.chunkIndex ASC
            """)
    List<SourceChunk> findExactByNotebookAndSources(
            UUID notebookId,
            List<UUID> sourceIds,
            String phrase,
            Pageable pageable
    );

    @Query(value = """
            SELECT * FROM source_chunks
            WHERE notebook_id = :notebookId
              AND MATCH(content) AGAINST (:query IN NATURAL LANGUAGE MODE)
            ORDER BY MATCH(content) AGAINST (:query IN NATURAL LANGUAGE MODE) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<SourceChunk> searchByNotebook(UUID notebookId, String query, int limit);

    @Query(value = """
            SELECT * FROM source_chunks
            WHERE notebook_id = :notebookId
              AND source_id IN (:sourceIds)
              AND MATCH(content) AGAINST (:query IN NATURAL LANGUAGE MODE)
            ORDER BY MATCH(content) AGAINST (:query IN NATURAL LANGUAGE MODE) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<SourceChunk> searchByNotebookAndSources(UUID notebookId, List<UUID> sourceIds, String query, int limit);
}
