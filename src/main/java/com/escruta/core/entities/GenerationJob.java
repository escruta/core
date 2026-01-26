package com.escruta.core.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Table(name = "generation_jobs")
@Entity
@NoArgsConstructor
public class GenerationJob {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "notebook_id", nullable = false)
    private Notebook notebook;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant completedAt;

    public GenerationJob(Notebook notebook, User user, JobType type) {
        this.notebook = notebook;
        this.user = user;
        this.type = type;
    }

    public void markAsProcessing() {
        this.status = JobStatus.PROCESSING;
    }

    public void markAsCompleted(String result) {
        this.status = JobStatus.COMPLETED;
        this.result = result;
        this.completedAt = Instant.now();
    }

    public void markAsFailed(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
    }

    public enum JobType {
        // AUDIO_SUMMARY,
        MIND_MAP,
        STUDY_GUIDE,
        FLASHCARDS,
        QUESTIONNAIRE
    }

    public enum JobStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
