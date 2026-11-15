package com.escruta.core.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Table(name = "generation_jobs")
@Entity
@DynamicUpdate
@NoArgsConstructor
public class GenerationJob {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notebook_id", nullable = false)
    private Notebook notebook;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobType type;

    @Column(nullable = false)
    private String title;

    @Column(name = "has_custom_title", nullable = false)
    private boolean hasCustomTitle = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String options;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant completedAt;

    public GenerationJob(Notebook notebook, User user, JobType type) {
        this(notebook, user, type, type.defaultTitle(), false, null);
    }

    public GenerationJob(
            Notebook notebook,
            User user,
            JobType type,
            String title,
            boolean hasCustomTitle,
            String options
    ) {
        this.notebook = notebook;
        this.user = user;
        this.type = type;
        this.title = title != null && !title.isBlank() ?
                title.trim() :
                type.defaultTitle();
        this.hasCustomTitle = hasCustomTitle;
        this.options = options;
    }

    public void markAsCompleted(String result) {
        this.status = JobStatus.COMPLETED;
        this.result = result;
        this.errorMessage = null;
        this.completedAt = Instant.now();
    }

    public void markAsFailed(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
    }

    public void resetForRetry() {
        this.status = JobStatus.PENDING;
        this.result = null;
        this.errorMessage = null;
        this.completedAt = null;
    }

    public enum JobType {
        MIND_MAP,
        STUDY_GUIDE,
        FLASHCARDS,
        QUESTIONNAIRE;

        public String defaultTitle() {
            return switch (this) {
                case MIND_MAP -> "Mind Map";
                case STUDY_GUIDE -> "Study Guide";
                case FLASHCARDS -> "Flashcards";
                case QUESTIONNAIRE -> "Questionnaire";
            };
        }
    }

    public enum JobStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
