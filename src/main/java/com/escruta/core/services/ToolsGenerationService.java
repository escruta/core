package com.escruta.core.services;

import com.escruta.core.dtos.tools.FlashcardsResponse;
import com.escruta.core.dtos.tools.MindMapResponse;
import com.escruta.core.dtos.tools.QuestionnaireResponse;
import com.escruta.core.dtos.tools.StudyGuideResponse;
import com.escruta.core.entities.GenerationJob;
import com.escruta.core.entities.GenerationJob.JobStatus;
import com.escruta.core.entities.GenerationJob.JobType;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.User;
import com.escruta.core.repositories.GenerationJobRepository;
import com.escruta.core.repositories.NotebookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ToolsGenerationService {
    private final GenerationJobRepository jobRepository;
    private final NotebookRepository notebookRepository;
    private final RetrievalService retrievalService;
    private final SourceService sourceService;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    @Transactional
    public GenerationJob createJob(UUID notebookId, User user, JobType type) {
        Notebook notebook = notebookRepository
                .findById(notebookId)
                .orElseThrow(() -> new IllegalArgumentException("Notebook not found"));

        boolean hasActiveJob = jobRepository.existsByNotebookIdAndUserIdAndTypeAndStatusIn(
                notebookId,
                user.getId(),
                type,
                List.of(JobStatus.PENDING, JobStatus.PROCESSING)
        );

        if (hasActiveJob) {
            throw new IllegalStateException("A job of this type is already in progress");
        }

        GenerationJob job = new GenerationJob(notebook, user, type);
        return jobRepository.save(job);
    }

    public Optional<GenerationJob> getJob(UUID jobId, UUID userId) {
        return jobRepository.findByIdAndUserId(jobId, userId);
    }

    public List<GenerationJob> getJobsForNotebook(UUID notebookId, UUID userId) {
        return jobRepository.findByNotebookIdAndUserIdOrderByCreatedAtDesc(notebookId, userId);
    }

    public Optional<GenerationJob> getLatestCompletedJob(UUID notebookId, UUID userId, JobType type) {
        return jobRepository.findLatestCompletedByType(notebookId, userId, type);
    }

    public List<GenerationJob> getActiveJobs(UUID notebookId, UUID userId, JobType type) {
        return jobRepository.findActiveJobsByType(
                notebookId,
                userId,
                type,
                List.of(JobStatus.PENDING, JobStatus.PROCESSING)
        );
    }

    @Async
    @Transactional
    public void processJob(UUID jobId) {
        GenerationJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }

        try {
            job.markAsProcessing();
            jobRepository.save(job);

            String result = generateContent(job);

            job.markAsCompleted(result);
            jobRepository.save(job);
        } catch (Exception e) {
            job.markAsFailed(e.getMessage());
            jobRepository.save(job);
        }
    }

    private String generateContent(GenerationJob job) throws Exception {
        UUID notebookId = job.getNotebook().getId();

        if (sourceService.hasNoSources(notebookId)) {
            throw new IllegalStateException("No sources available in this notebook");
        }

        String query = "core concepts key ideas summary main topic definitions principles overview";
        List<Document> documents = retrievalService.getDocumentsForNotebook(notebookId, query, 30);
        if (documents.isEmpty()) {
            throw new IllegalStateException("Content not yet indexed");
        }

        String context = documents
                .stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElseThrow(() -> new IllegalStateException("No content available"));

        return switch (job.getType()) {
            case STUDY_GUIDE -> generateStudyGuide(context);
            case FLASHCARDS -> generateFlashcards(context);
            case QUESTIONNAIRE -> generateQuestionnaire(context);
            case MIND_MAP -> generateMindMap(context);
        };
    }

    private String generateStudyGuide(String context) throws Exception {
        StudyGuideResponse response = ChatClient
                .create(chatModel)
                .prompt()
                .system("""
                        You are an expert educator. Your task is to analyze the provided materials and create a comprehensive study guide.
                        
                        First, identify the **CENTRAL THEME** that unifies all the materials.
                        The entire study guide must revolve around this core subject.
                        
                        The study guide must include:
                        - overview: A clear, high-level introduction to the identified central theme (3-4 sentences)
                        - keyConcepts: List of the most important terms and their definitions, essential for understanding the topic
                        - importantDetails: Crucial supporting information, examples, and key facts found in the materials
                        - connections: Explanation of how the different concepts relate to each other to form the central theme
                        - reviewQuestions: Challenging questions that test deep understanding of the core ideas
                        """)
                .user("Identify the central theme and create a comprehensive study guide from these materials:\n\n" + context)
                .call()
                .entity(StudyGuideResponse.class);

        return objectMapper.writeValueAsString(response);
    }

    private String generateFlashcards(String context) throws Exception {
        FlashcardsResponse response = ChatClient
                .create(chatModel)
                .prompt()
                .system("""
                        You are an expert educator. Analyze the provided materials to identify the **CORE CONCEPTS** and **KEY DEFINITIONS**.
                        
                        Identify the primary subject matter and create 12-18 high-quality flashcards for effective learning.
                        Each flashcard has:
                        - front: A clear question or term
                        - back: A concise but complete answer or definition
                        
                        Ensure the flashcards cover the most essential information found across all the materials.
                        """)
                .user("Create essential flashcards from these materials:\n\n" + context)
                .call()
                .entity(FlashcardsResponse.class);

        return objectMapper.writeValueAsString(response);
    }

    private String generateQuestionnaire(String context) throws Exception {
        QuestionnaireResponse response = ChatClient
                .create(chatModel)
                .prompt()
                .system("""
                        You are an expert educator. Analyze the provided materials to identify the **CENTRAL THEME**.
                        
                        Create a comprehensive questionnaire to test deep understanding of this subject.
                        Focus on how different concepts interrelate within the main theme.
                        
                        Create 10-15 questions with a mix of types:
                        - type: "multiple_choice", "true_false", or "short_answer"
                        - question: The question text (focused on concepts, not just trivia)
                        - options: List of options (only for multiple_choice, use null otherwise)
                        - correctAnswerIndex: Index of correct option (only for multiple_choice, use null otherwise)
                        - correctAnswerBoolean: true/false (only for true_false, use null otherwise)
                        - sampleAnswer: Expected answer (only for short_answer, use null otherwise)
                        - explanation: A detailed explanation of why this answer is correct and how it relates to the central theme
                        
                        Provide a descriptive title for the questionnaire that reflects the identified central theme.
                        """)
                .user("Identify the central theme and create a conceptual questionnaire from these materials:\n\n" + context)
                .call()
                .entity(QuestionnaireResponse.class);

        return objectMapper.writeValueAsString(response);
    }

    private String generateMindMap(String context) throws Exception {
        MindMapResponse response = ChatClient
                .create(chatModel)
                .prompt()
                .system("""
                        You are an expert at creating mind maps. Analyze the provided materials to identify the **CORE ESSENCE** and logical structure.
                        
                        The mind map must have:
                        - central: The main topic (the core essence derived from all sources)
                        - branches: List of 5-7 main branches representing the primary sub-themes, each with:
                          - label: The branch name
                          - children: List of sub-branches representing details and related concepts (can be nested)
                        
                        Ensure the hierarchy is logical and helps visualize the relationships between the core concepts found in the materials.
                        """)
                .user("Identify the central theme and create a hierarchical mind map from these materials:\n\n" + context)
                .call()
                .entity(MindMapResponse.class);

        return objectMapper.writeValueAsString(response);
    }
}
