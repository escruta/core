package com.escruta.core.entities;

import com.escruta.core.entities.enums.SourceStatus;
import com.escruta.core.entities.enums.SourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@Table(name = "sources")
@Entity
public class Source {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Notebook notebook;

    @Column()
    private String icon;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean isConvertedByAi = false;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceStatus status = SourceStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column()
    private SourceType type;

    @Column()
    private String link;

    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;

    @UpdateTimestamp
    @Column()
    private Timestamp updatedAt;
}
