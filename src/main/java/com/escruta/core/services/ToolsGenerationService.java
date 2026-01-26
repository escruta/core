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

        if (!sourceService.hasSources(notebookId)) {
            throw new IllegalStateException("No sources available in this notebook");
        }

        List<Document> documents = retrievalService.getDocumentsForNotebook(notebookId, 10);
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
                        You are an expert educator. Create a comprehensive study guide based on the provided content.
                        
                        The study guide must include:
                        - overview: A brief introduction to the topic (2-3 sentences)
                        - keyConcepts: List of key terms with their definitions
                        - importantDetails: List of supporting information and examples
                        - connections: List of how concepts relate to each other
                        - reviewQuestions: List of questions to test understanding
                        """)
                .user("Create a study guide from this content:\n\n" + context)
                .call()
                .entity(StudyGuideResponse.class);

        return objectMapper.writeValueAsString(response);
    }

    private String generateFlashcards(String context) throws Exception {
        FlashcardsResponse response = ChatClient.create(chatModel).prompt().system("""
                You are an expert educator. Create flashcards for effective spaced repetition learning.
                
                Create 10-15 flashcards covering the most important concepts.
                Each flashcard has:
                - front: A question or term
                - back: The answer or definition (concise but complete)
                """).user("Create flashcards from this content:\n\n" + context).call().entity(FlashcardsResponse.class);

        return objectMapper.writeValueAsString(response);
    }

    private String generateQuestionnaire(String context) throws Exception {
        QuestionnaireResponse response = ChatClient
                .create(chatModel)
                .prompt()
                .system("""
                        You are an expert educator. Create a comprehensive questionnaire to test understanding.
                        
                        Create 10-12 questions with a mix of types:
                        - type: "multiple_choice", "true_false", or "short_answer"
                        - question: The question text
                        - options: List of options (only for multiple_choice, use null otherwise)
                        - correctAnswerIndex: Index of correct option (only for multiple_choice, use null otherwise)
                        - correctAnswerBoolean: true/false (only for true_false, use null otherwise)
                        - sampleAnswer: Expected answer (only for short_answer, use null otherwise)
                        - explanation: Why this answer is correct
                        
                        Include a title for the questionnaire.
                        """)
                .user("Create a questionnaire from this content:\n\n" + context)
                .call()
                .entity(QuestionnaireResponse.class);

        return objectMapper.writeValueAsString(response);
    }

    private String generateMindMap(String context) throws Exception {
        MindMapResponse response = ChatClient.create(chatModel).prompt().system("""
                You are an expert at creating mind maps. Analyze the content and create a hierarchical mind map structure.
                
                The mind map must have:
                - central: The main topic
                - branches: List of main branches, each with:
                  - label: The branch name
                  - children: List of sub-branches (can be nested)
                
                Create a well-organized mind map with 4-6 main branches and relevant sub-topics.
                """).user("Create a mind map from this content:\n\n" + context).call().entity(MindMapResponse.class);

        return objectMapper.writeValueAsString(response);
    }
}
