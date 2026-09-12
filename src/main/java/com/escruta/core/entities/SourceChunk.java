package com.escruta.core.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Table(name = "source_chunks")
@Entity
public class SourceChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "notebook_id", nullable = false)
    private UUID notebookId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false)
    private String title;

    @Column()
    private String link;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
}
