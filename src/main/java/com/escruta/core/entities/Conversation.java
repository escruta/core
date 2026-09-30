package com.escruta.core.entities;

import com.escruta.core.entities.enums.ChatMode;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatMode mode = ChatMode.NORMAL;

    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;

    @UpdateTimestamp
    @Column()
    private Timestamp updatedAt;
}
