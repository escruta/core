package com.escruta.core.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@Table(name = "notes")
@Entity
@DynamicUpdate
public class Note {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Notebook notebook;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_group_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private NoteGroup noteGroup;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;

    @UpdateTimestamp
    @Column()
    private Timestamp updatedAt;
}
