package com.escruta.core.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;

@Getter
@Setter
@Table(name = "conversations")
@Entity
public class Conversation {
    @Id
    @Column(nullable = false)
    private String id;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Notebook notebook;

    @Column(nullable = false)
    private String title;

    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;

    @UpdateTimestamp
    @Column()
    private Timestamp updatedAt;

    @PrePersist
    @PreUpdate
    @PreRemove
    private void touchNotebook() {
        if (notebook != null) {
            notebook.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        }
    }
}
