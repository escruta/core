package com.escruta.core.entities;

import com.escruta.core.entities.enums.ProficiencyLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Table(name = "notebook_learning_memories")
@Entity
@DynamicUpdate
public class NotebookLearningMemory {
    @Id
    @Column(name = "notebook_id", nullable = false)
    private UUID notebookId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "notebook_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Notebook notebook;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "proficiency_level", nullable = false, length = 32)
    private ProficiencyLevel proficiencyLevel = ProficiencyLevel.AUTO;

    @Column(name = "learning_goal", columnDefinition = "TEXT")
    private String learningGoal;

    @Column(name = "mastered_concepts", columnDefinition = "TEXT")
    private String masteredConcepts;

    @Column(name = "struggling_concepts", columnDefinition = "TEXT")
    private String strugglingConcepts;

    @Column(name = "study_notes", columnDefinition = "TEXT")
    private String studyNotes;

    @Column(name = "flashcards_reviewed", nullable = false)
    private int flashcardsReviewed = 0;

    @Column(name = "flashcards_known", nullable = false)
    private int flashcardsKnown = 0;

    @Column(name = "quiz_questions_answered", nullable = false)
    private int quizQuestionsAnswered = 0;

    @Column(name = "quiz_questions_correct", nullable = false)
    private int quizQuestionsCorrect = 0;

    @Column(name = "learning_turns_count", nullable = false)
    private int learningTurnsCount = 0;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Timestamp createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Timestamp updatedAt;

    public NotebookLearningMemory(Notebook notebook) {
        this.notebook = notebook;
    }
}
