package com.escruta.core.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Table(name = "notebooks")
@Entity
@DynamicUpdate
@RequiredArgsConstructor
public class Notebook {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn()
    private Folder folder;

    @Column()
    private String icon;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String summaryTopics;

    @OneToMany(mappedBy = "notebook")
    private List<Source> sources;

    @OneToMany(mappedBy = "notebook")
    private List<SourceGroup> sourceGroups;

    @OneToMany(mappedBy = "notebook")
    private List<NoteGroup> noteGroups;

    @OneToMany(mappedBy = "notebook")
    private List<Note> notes;

    @OneToMany(mappedBy = "notebook")
    private List<GenerationJob> generationJobs;

    @OneToMany(mappedBy = "notebook")
    private List<Conversation> conversations;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "last_activity_at", insertable = false, updatable = false)
    private Timestamp lastActivityAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;

    @UpdateTimestamp
    @Column()
    private Timestamp updatedAt;
}
