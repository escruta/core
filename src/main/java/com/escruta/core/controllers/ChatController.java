package com.escruta.core.controllers;

import com.escruta.core.dtos.ChatRequest;
import com.escruta.core.dtos.ChatReplyMessage;
import com.escruta.core.dtos.ExampleQuestions;
import com.escruta.core.dtos.SummaryResponse;
import com.escruta.core.entities.Conversation;
import com.escruta.core.repositories.ConversationRepository;
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
            5. For mathematical expressions, ALWAYS use LaTeX format with dollar signs:
               - Inline math: $...$ (e.g., $\\alpha$, $|0\\rangle$, $\\psi$)
               - Block math: $$...$$ (e.g., $$|\\psi\\rangle = \\alpha|0\\rangle + \\beta|1\\rangle$$)
               - NEVER use parentheses like (\\alpha) or (|0\\rangle)
            """;

    private static final String UNIFIED_SUMMARY_SYSTEM_MESSAGE = """
            You are an expert at identifying the core essence and central themes of a set of study materials.
            Your task is to write a comprehensive summary paragraph of 4-6 lines about the central subject matter of this notebook.
            
            RULES:
            - Focus on the **CENTRAL THEME** and **UNIFYING CONCEPTS** of all provided sources
            - Identify the main subject and its most significant aspects
            - Use **bold** for key terms and *italic* for emphasis (sparingly)
            - Write as if explaining the topic directly to a student, providing a clear bird's-eye view
            - Do NOT start with phrases like "The articles...", "The sources...", "This content..." or similar
            - Start directly with the core subject matter (e.g., "Quantum computing is a field that...")
            - Ensure the summary provides a cohesive understanding of how the various pieces of information relate to each other
            """;

    private final SourceService sourceService;
    private final RetrievalService retrievalService;
    private final ChatModel chatModel;
    private final NotebookRepository notebookRepository;
    private final ConversationRepository conversationRepository;
    private final JdbcChatMemoryRepository chatMemoryRepository;

    private Optional<String> getNotebookContext(UUID notebookId, int documentLimit) {
        if (sourceService.hasNoSources(notebookId)) {
            return Optional.empty();
        }

        String query = "core concepts key ideas summary main topic definitions overview";
        var documents = retrievalService.getDocumentsForNotebook(notebookId, query, documentLimit);
        if (documents.isEmpty()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            documents = retrievalService.getDocumentsForNotebook(notebookId, query, documentLimit);
            if (documents.isEmpty()) {
                return Optional.empty();
            }
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
    ResponseEntity<SummaryResponse> generateSummary(@PathVariable UUID notebookId) {
        Optional<String> contextOpt = getNotebookContext(notebookId, 20);

        if (contextOpt.isEmpty()) {
            throw new IllegalStateException("No sources available or content not yet indexed");
        }

        String context = contextOpt.get();
        notebookRepository.updateSummary(notebookId, null);

        int chunkSize = 50000;
        String finalSummaryText;

        if (context.length() > chunkSize) {
            StringBuilder intermediateSummaries = new StringBuilder();
            int start = 0;
            while (start < context.length()) {
                int end = Math.min(start + chunkSize, context.length());
                String chunk = context.substring(start, end);

                SummaryResponse chunkSummary = ChatClient
                        .create(chatModel)
                        .prompt()
                        .system(UNIFIED_SUMMARY_SYSTEM_MESSAGE)
                        .user("Analyze the following materials and write a high-level summary that captures the central theme and core concepts of this subject matter:\n\n" + chunk)
                        .call()
                        .entity(SummaryResponse.class);

                if (chunkSummary != null && chunkSummary.summary() != null) {
                    intermediateSummaries.append(chunkSummary.summary()).append("\n\n");
                }
                start = end;
            }

            SummaryResponse finalSummary = ChatClient
                    .create(chatModel)
                    .prompt()
                    .system(UNIFIED_SUMMARY_SYSTEM_MESSAGE)
                    .user("Analyze the following materials (which are partial summaries) and write a single, cohesive high-level summary that captures the central theme and core concepts of the entire subject matter:\n\n" + intermediateSummaries)
                    .call()
                    .entity(SummaryResponse.class);
            finalSummaryText = finalSummary != null ?
                    finalSummary.summary() :
                    null;

        } else {
            SummaryResponse summary = ChatClient
                    .create(chatModel)
                    .prompt()
                    .system(UNIFIED_SUMMARY_SYSTEM_MESSAGE)
                    .user("Analyze the following materials and write a high-level summary that captures the central theme and core concepts of this subject matter:\n\n" + context)
                    .call()
                    .entity(SummaryResponse.class);
            finalSummaryText = summary != null ?
                    summary.summary() :
                    null;
        }

        if (finalSummaryText == null || finalSummaryText.trim().isEmpty()) {
            throw new RuntimeException("Failed to generate summary: empty response from AI");
        }

        notebookRepository.updateSummary(notebookId, finalSummaryText);
        return ResponseEntity.ok(new SummaryResponse(finalSummaryText));
    }

    @GetMapping("summary")
    ResponseEntity<SummaryResponse> getSummary(@PathVariable UUID notebookId) {
        var notebook = notebookRepository.findById(notebookId).orElse(null);

        if (notebook == null) {
            return ResponseEntity.notFound().build();
        }

        String summary = notebook.getSummary();
        if (summary == null || summary.trim().isEmpty()) {
            return ResponseEntity.ok(new SummaryResponse(""));
        }

        return ResponseEntity.ok(new SummaryResponse(summary));
    }

    @DeleteMapping("summary")
    ResponseEntity<Void> deleteSummary(@PathVariable UUID notebookId) {
        var notebook = notebookRepository.findById(notebookId).orElse(null);

        if (notebook == null) {
            return ResponseEntity.notFound().build();
        }

        notebookRepository.updateSummary(notebookId, null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("example-questions")
    public ResponseEntity<ExampleQuestions> getExampleQuestions(@PathVariable UUID notebookId) {
        Optional<String> context = getNotebookContext(notebookId, 15);

        if (context.isEmpty()) {
            return ResponseEntity.ok(new ExampleQuestions(List.of()));
        }

        ExampleQuestions exampleQuestions = ChatClient.create(chatModel).prompt().user("""
                You are an expert tutor. Based on the provided materials, generate exactly 3 questions that help a student understand the core concepts and the central theme of this subject.
                
                RULES:
                - Focus on **FUNDAMENTAL CONCEPTS** and key ideas that someone learning this topic must understand
                - Ask about **DEFINITIONS**, **MECHANISMS**, **RELATIONSHIPS** between concepts, or **CAUSE-EFFECT**
                - Questions must be about the **SUBJECT MATTER** itself, not about the text
                - Do NOT mention "article", "document", "text", "source", or "Wikipedia"
                - Do NOT ask what the topic is, what is covered, or ask for lists/summaries
                - Use "why", "how", or "what is the relationship between" type questions
                - Each question must be answerable using ONLY the provided information
                - Make questions specific enough to require understanding, not vague general questions
                
                BAD examples:
                - "What is the main topic?" (too vague)
                - "What does the text say about X?" (about the text)
                - "List the types of X" (just listing)
                
                GOOD examples:
                - "Why does X happen when Y occurs?" (mechanism/cause-effect)
                - "How is X different from Y?" (conceptual distinction)
                - "What is the relationship between X and Y?" (interconnection)
                
                MATERIALS:
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
    }

    @PostMapping("chat")
    ResponseEntity<ChatReplyMessage> generation(
            @PathVariable UUID notebookId,
            @Valid @RequestBody ChatRequest request
    ) {
        var notebook = notebookRepository.findById(notebookId).orElse(null);
        if (notebook == null) {
            return ResponseEntity.notFound().build();
        }

        ChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();

        var chatClient = ChatClient.builder(chatModel).defaultSystem(UNIFIED_SYSTEM_MESSAGE).defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                retrievalService.getQuestionAnswerAdvisor(notebookId, request.selectedSourceIds())
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

        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation == null) {
            conversation = new Conversation();
            conversation.setId(conversationId);
            conversation.setNotebook(notebook);

            String title;
            try {
                title = ChatClient
                        .create(chatModel)
                        .prompt()
                        .system("Generate a very short and concise title (maximum 5 words) for a conversation that starts with the following message. Respond ONLY with the title, without quotes or extra punctuation. Use the same language as the user's message.")
                        .user(request.userInput())
                        .call()
                        .content();

                if (title != null && !title.isBlank()) {
                    title = title.replaceAll("^[\"']|[\"']$", "");
                } else {
                    title = request.userInput();
                }
            } catch (Exception e) {
                title = request.userInput();
            }

            if (title.length() > 100) {
                title = title.substring(0, 97) + "...";
            }
            conversation.setTitle(title.trim());
        }
        conversationRepository.save(conversation);

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
                conversation.getTitle(),
                citedSources
        ));
    }
}
