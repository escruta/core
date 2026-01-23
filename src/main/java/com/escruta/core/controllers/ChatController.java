package com.escruta.core.controllers;

import com.escruta.core.dtos.ChatRequest;
import com.escruta.core.dtos.ChatReplyMessage;
import com.escruta.core.dtos.ExampleQuestions;
import com.escruta.core.dtos.SummaryResponse;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.services.SourceService;
import com.escruta.core.services.RetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.*;

@RestController
@RequestMapping("notebooks/{notebookId}")
@RequiredArgsConstructor
class ChatController {
    private static final String UNIFIED_SYSTEM_MESSAGE = """
            You are a helpful AI assistant. Answer questions using ONLY the provided sources.
            
            RULES:
            1. Provide clear, comprehensive answers based on the available sources
            2. Write in a natural, conversational tone
            3. Use simple formatting only: **bold**, *italic*, `code`
            4. Focus on directly answering the user's question with the information from the sources
            """;

    private static final String UNIFIED_SUMMARY_SYSTEM_MESSAGE = """
            Write a summary paragraph of 4-5 lines about the content provided.
            
            RULES:
            - Use **bold** for key terms and *italic* for emphasis (sparingly)
            - Write as if explaining the topic directly, not describing the sources
            - Do NOT start with "The articles...", "The sources...", "This content..." or similar
            - Start directly with the subject matter (e.g., "Quantum computing is...")
            - Define or mention the main concepts
            """;

    private final SourceService sourceService;
    private final RetrievalService retrievalService;
    private final ChatModel chatModel;
    private final NotebookRepository notebookRepository;
    private final JdbcChatMemoryRepository chatMemoryRepository;

    private Optional<String> getNotebookContext(UUID notebookId, int documentLimit) {
        if (!sourceService.hasSources(notebookId)) {
            return Optional.empty();
        }

        var documents = retrievalService.getDocumentsForNotebook(notebookId, documentLimit);
        if (documents.isEmpty()) {
            return Optional.empty();
        }

        String context = documents
                .stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");

        return context.isBlank() ?
                Optional.empty() :
                Optional.of(context);
    }

    @PostMapping("summary")
    ResponseEntity<String> generateSummary(@PathVariable UUID notebookId) {
        try {
            Optional<String> context = getNotebookContext(notebookId, 5);

            if (context.isEmpty()) {
                return ResponseEntity.badRequest().body("No sources available or content not yet indexed.");
            }

            SummaryResponse summary = ChatClient
                    .create(chatModel)
                    .prompt()
                    .system(UNIFIED_SUMMARY_SYSTEM_MESSAGE)
                    .user("Write a summary paragraph about this:\n\n" + context.get())
                    .call()
                    .entity(SummaryResponse.class);

            assert summary != null;
            notebookRepository.updateSummary(notebookId, summary.summary());
            return ResponseEntity.ok(summary.summary());
        } catch (Exception e) {
            return ResponseEntity
                    .internalServerError()
                    .body("An error occurred while generating the summary. Please try again.");
        }
    }

    @GetMapping("summary")
    ResponseEntity<String> getSummary(@PathVariable UUID notebookId) {
        try {
            var notebook = notebookRepository.findById(notebookId).orElse(null);

            if (notebook == null) {
                return ResponseEntity.notFound().build();
            }

            String summary = notebook.getSummary();
            if (summary == null || summary.trim().isEmpty()) {
                return ResponseEntity.ok("");
            }

            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity
                    .internalServerError()
                    .body("An error occurred while retrieving the summary. Please try again.");
        }
    }

    @GetMapping("example-questions")
    public ResponseEntity<?> getExampleQuestions(@PathVariable UUID notebookId) {
        try {
            Optional<String> context = getNotebookContext(notebookId, 3);

            if (context.isEmpty()) {
                return ResponseEntity.badRequest().body("No sources available or content not yet indexed.");
            }

            ExampleQuestions exampleQuestions = ChatClient.create(chatModel).prompt().user("""
                    Generate exactly 3 questions based on this text.
                    
                    RULES:
                    - Questions must be about the SUBJECT MATTER, not about the text itself
                    - Do NOT mention "article", "document", "text", "source", or "Wikipedia"
                    - Do NOT ask what the topic is or what is covered
                    - Ask questions that someone studying this subject would ask
                    - Each question must be answerable using ONLY the provided information
                    
                    %s
                    """.formatted(context.get())).call().entity(ExampleQuestions.class);

            if (exampleQuestions != null && exampleQuestions.questions() != null) {
                List<String> limitedQuestions = exampleQuestions
                        .questions()
                        .stream()
                        .filter(q -> q != null && !q.isBlank())
                        .limit(3)
                        .toList();
                return ResponseEntity.ok(new ExampleQuestions(limitedQuestions));
            }

            return ResponseEntity.ok(new ExampleQuestions(List.of()));
        } catch (Exception e) {
            return ResponseEntity
                    .internalServerError()
                    .body("An error occurred while generating the questions. Please try again.");
        }
    }

    @PostMapping("chat")
    ResponseEntity<ChatReplyMessage> generation(
            @PathVariable UUID notebookId,
            @Valid @RequestBody ChatRequest request
    ) {
        try {
            ChatMemory chatMemory = MessageWindowChatMemory
                    .builder()
                    .chatMemoryRepository(chatMemoryRepository)
                    .maxMessages(10)
                    .build();

            var chatClient = ChatClient.builder(chatModel).defaultSystem(UNIFIED_SYSTEM_MESSAGE).defaultAdvisors(
                    MessageChatMemoryAdvisor.builder(chatMemory).build(),
                    retrievalService.getQuestionAnswerAdvisor(notebookId)
            ).build();

            String conversationId = request.conversationId() != null ?
                    request.conversationId() :
                    UUID.randomUUID().toString();

            var chatResponse = chatClient
                    .prompt()
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .user(request.userInput())
                    .call()
                    .chatResponse();

            assert chatResponse != null;
            List<Document> documents = chatResponse
                    .getMetadata()
                    .getOrDefault(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS, List.of());

            List<ChatReplyMessage.CitedSource> citedSources = documents
                    .stream()
                    .map(doc -> new ChatReplyMessage.CitedSource(
                            UUID.fromString(doc
                                    .getMetadata()
                                    .get("sourceId")
                                    .toString()),
                            doc.getMetadata().get("title").toString()
                    ))
                    .distinct()
                    .toList();

            return ResponseEntity.ok(new ChatReplyMessage(
                    chatResponse.getResult().getOutput().getText(),
                    conversationId,
                    citedSources
            ));
        } catch (Exception e) {
            return ResponseEntity
                    .internalServerError()
                    .body(new ChatReplyMessage(
                            "An error occurred while processing your request. Please try again.",
                            null,
                            List.of()
                    ));
        }
    }
}