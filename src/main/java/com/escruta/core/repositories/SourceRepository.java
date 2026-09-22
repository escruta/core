package com.escruta.core.repositories;

import com.escruta.core.entities.Source;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SourceRepository extends CrudRepository<Source, UUID> {
    List<Source> findByNotebookId(UUID notebookId);

    List<Source> findByNotebookIdAndSourceGroupId(UUID notebookId, UUID sourceGroupId);

    List<Source> findBySourceGroupId(UUID sourceGroupId);

    boolean existsByNotebookId(UUID notebookId);

    @Query("select s.link from Source s where s.notebook.id = :notebookId and s.link is not null")
    List<String> findLinksByNotebookId(UUID notebookId);

    @Query("select s.notebook.id from Source s where s.id = :sourceId")
    UUID findNotebookId(UUID sourceId);

    @Query("select s.notebook.user.id from Source s where s.id = :sourceId")
    UUID findNotebookOwnerId(UUID sourceId);
}
