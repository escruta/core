package com.escruta.core.repositories;

import com.escruta.core.entities.SourceChunk;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SourceChunkRepository extends JpaRepository<SourceChunk, UUID> {
    void deleteBySourceId(UUID sourceId);

    List<SourceChunk> findByNotebookIdOrderByChunkIndexAsc(UUID notebookId, Pageable pageable);

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
