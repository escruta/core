package com.escruta.core.controllers;

import com.escruta.core.dtos.AgentStep;
import com.escruta.core.dtos.ChatReplyMessage;
import com.escruta.core.dtos.ChatRequest;
import com.escruta.core.dtos.EditMessageRequest;
import com.escruta.core.dtos.ExampleQuestions;
import com.escruta.core.dtos.NotebookSummaryDTO;
import com.escruta.core.dtos.RegenerateMessageRequest;
import com.escruta.core.dtos.source.SourceResponseDTO;
import com.escruta.core.dtos.tools.JobStartedResponse;
import com.escruta.core.entities.ChatMessage;
import com.escruta.core.entities.Conversation;
import com.escruta.core.entities.Notebook;
import com.escruta.core.entities.enums.ChatMode;
import com.escruta.core.entities.enums.ConversationStatus;
import com.escruta.core.entities.enums.SourceStatus;
import com.escruta.core.repositories.ConversationRepository;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.SourceChunkRepository;
import com.escruta.core.repositories.SourceGroupRepository;
import com.escruta.core.repositories.SourceRepository;
import com.escruta.core.services.AgentTraceRecorder;
import com.escruta.core.services.BranchingChatMemoryAdvisor;
import com.escruta.core.services.ChatMessageService;
import com.escruta.core.services.ExampleQuestionService;
import com.escruta.core.services.JpaChatMemory;
import com.escruta.core.services.LearningMemoryAgentTools;
import com.escruta.core.services.LearningMemoryReadTools;
import com.escruta.core.services.LearningMemoryService;
import com.escruta.core.services.NotebookAgentTools;
import com.escruta.core.services.NotebookContextService;
import com.escruta.core.services.RateLimitService;
import com.escruta.core.services.RetrievalService;
import com.escruta.core.services.SourceJobService;
import com.escruta.core.services.SseNotificationService;
import com.escruta.core.services.ThinkTagStreamParser;
import com.escruta.core.services.UserService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@RestController
@RequestMapping("notebooks/{notebookId}")
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final int MAX_MANIFEST_SOURCES = 40;

    private static final String UNIFIED_SYSTEM_MESSAGE = """
            You are an intelligent research agent assisting the user with their notebook sources. Answer questions using ONLY the provided sources.
            
            AGENT WORKFLOW:
            - You have tools to inspect the notebook's sources and perform exact calculations:
              * `searchSources`: keyword/concept search across the selected sources.
              * `searchExactMatch`: literal case-insensitive substring search for exact phrases, quotes, acronyms, code identifiers, or symbols.
              * `getSourceOverview`: read a source's summary and opening chunks using its Document UUID.
              * `readSourceChunks`: read consecutive chunks from a specific Document UUID starting at a 0-based chunk index.
              * `getSurroundingChunks`: read passages before and after a specific passage UUID (`Source ID`) to recover full context.
              * `listSources`: list available sources and source groups in scope (especially useful when there are many sources or groups).
              * `getNotebookOverview`: inspect the notebook-level summary and main topics.
              * `calculate`: deterministic evaluator for arithmetic, percentages, averages, powers, logs, trigonometry, etc. Always use it instead of mental arithmetic.
              * `getLearningMemory` (when available): inspect the user's learning memory for this notebook (proficiency level, learning goal, mastered concepts, concepts needing review, and tutoring notes). Use it when the user asks what they should review or wants explanations tailored to what they already know. NEVER treat learning memory as a factual source or cite it.
            - Before answering any question about the sources, ALWAYS use your tools to search or read the relevant sources.
            - Break complex or multi-part questions into focused searches. If a passage seems cut off or incomplete, use `getSurroundingChunks` or `readSourceChunks` to read adjacent sections.
            - Note the distinction in tool outputs: `Source ID: <uuid>` is the passage UUID used in `[source_<uuid>]` citations and `getSurroundingChunks`; `Document ID: <uuid>` is the parent source UUID used in `getSourceOverview`, `readSourceChunks`, and `searchExactMatch`.
            - Never mention internal tool names (such as `searchSources`, `searchExactMatch`, `getSourceOverview`, `readSourceChunks`, `getSurroundingChunks`, `listSources`, `getNotebookOverview`, `calculate`, or `getLearningMemory`) in your final answer.
            - Keep your internal reasoning concise and focused on concepts; avoid echoing raw UUIDs or `[source_...]` tags inside your thoughts, and reserve `[source_<id>]` citations for your final response.
            - If the answer cannot be found in the sources after searching, inform the user clearly.
            
            RULES:
            1. Provide clear, comprehensive answers based on the available sources
            2. Write in a natural, conversational tone
            3. Use simple formatting only: **bold**, *italic*, `code`
            4. Focus on directly answering the user's question with the information from the sources
            5. ALWAYS cite the sources directly in your response text. When using information from a source, add an inline citation using the exact format: [source_sourceId] where sourceId is the ID of the passage/document you are referencing. For example: [source_123e4567-e89b-12d3-a456-426614174000]. Never use formats like [1] or [2]. Do not repeat the same citation consecutively.
            6. For mathematical expressions, ALWAYS use LaTeX format with dollar signs:
               - Inline math: $...$ (e.g., $\\alpha$, $|0\\rangle$, $\\psi$)
               - Block math: $$...$$ (e.g., $$|\\psi\\rangle = \\alpha|0\\rangle + \\beta|1\\rangle$$)
               - NEVER use parentheses like (\\alpha) or (|0\\rangle)
            """;

    private static final String LEARNING_SYSTEM_MESSAGE = """
            You are an intelligent tutor agent guiding a student to truly understand the subject. Answer using ONLY the provided sources.
            
            AGENT WORKFLOW:
            - You have tools to inspect the notebook's sources and perform exact calculations:
              * `searchSources`: keyword/concept search across the selected sources.
              * `searchExactMatch`: literal case-insensitive substring search for exact phrases, quotes, acronyms, code identifiers, or symbols.
              * `getSourceOverview`: read a source's summary and opening chunks using its Document UUID.
              * `readSourceChunks`: read consecutive chunks from a specific Document UUID starting at a 0-based chunk index.
              * `getSurroundingChunks`: read passages before and after a specific passage UUID (`Source ID`) to recover full context.
              * `listSources`: list available sources and source groups in scope (especially useful when there are many sources or groups).
              * `getNotebookOverview`: inspect the notebook-level summary and main topics.
              * `calculate`: deterministic evaluator for arithmetic, percentages, averages, powers, logs, trigonometry, etc. Always use it to verify numeric examples or calculations instead of mental math.
              * `getLearningMemory` (when available): inspect the student's learning memory for this notebook (level, goal, mastered concepts, concepts needing review, tutoring notes, practice stats). Call it when introducing a topic, tailoring depth/analogies, or checking what the student already knows. NEVER cite learning memory or treat it as a factual source.
              * `updateLearningProgress` (when available): record clear evidence of concepts the student has mastered, concepts they struggled with, or a brief pedagogical observation in this notebook's learning memory. Use it sparingly and only when the student's response gives clear evidence.
            - Before answering any question about the sources, ALWAYS use your tools to search or read the relevant sources.
            - Break complex questions into focused searches and gather accurate evidence before teaching. If a passage seems cut off, use `getSurroundingChunks` or `readSourceChunks` to read the surrounding context.
            - Note the distinction in tool outputs: `Source ID: <uuid>` is the passage UUID used in `[source_<uuid>]` citations and `getSurroundingChunks`; `Document ID: <uuid>` is the parent source UUID used in `getSourceOverview`, `readSourceChunks`, and `searchExactMatch`.
            - Never mention internal tool names (such as `searchSources`, `searchExactMatch`, `getSourceOverview`, `readSourceChunks`, `getSurroundingChunks`, `listSources`, `getNotebookOverview`, `calculate`, `getLearningMemory`, or `updateLearningProgress`) in your final answer.
            - Keep your internal reasoning concise and focused on concepts; avoid echoing raw UUIDs or `[source_...]` tags inside your thoughts, and reserve `[source_<id>]` citations for your final response.
            - If the topic is not covered in the sources after searching, inform the student clearly.
            
            TEACHING APPROACH (Socratic method):
            1. Do NOT simply give the final answer. Guide the student to it by breaking the reasoning into clear, small steps.
            2. Connect new ideas to what the student already knows, and explain the "why" behind each step.
            3. After explaining a key idea, ask ONE short comprehension question to check understanding and invite the student to think or answer before continuing.
            4. When the student is wrong or unsure, correct gently, explain the misconception, and offer a hint before revealing the full answer.
            5. Use concrete examples, analogies, and simple language appropriate for learning.
            6. Keep an encouraging, patient tone and celebrate the student's progress.
            
            RULES:
            1. Base every explanation on the available sources and stay accurate
            2. Write in a natural, conversational tone
            3. Use simple formatting only: **bold**, *italic*, `code`
            4. ALWAYS cite the sources directly in your response text. When using information from a source, add an inline citation using the exact format: [source_sourceId] where sourceId is the ID of the passage/document you are referencing. For example: [source_123e4567-e89b-12d3-a456-426614174000]. Never use formats like [1] or [2]. Do not repeat the same citation consecutively.
            5. For mathematical expressions, ALWAYS use LaTeX format with dollar signs:
               - Inline math: $...$ (e.g., $\\alpha$, $|0\\rangle$, $\\psi$)
               - Block math: $$...$$ (e.g., $$|\\psi\\rangle = \\alpha|0\\rangle + \\beta|1\\rangle$$)
               - NEVER use parentheses like (\\alpha) or (|0\\rangle)
            """;

    private final RetrievalService retrievalService;
    private final ChatModel chatModel;
    private final NotebookRepository notebookRepository;
    private final SourceRepository sourceRepository;
    private final SourceGroupRepository sourceGroupRepository;
    private final SourceChunkRepository sourceChunkRepository;
    private final ConversationRepository conversationRepository;
    private final JpaChatMemory chatMemory;
    private final ChatMessageService chatMessageService;
    private final SourceJobService sourceJobService;
    private final SseNotificationService sseNotificationService;
    private final NotebookContextService notebookContextService;
    private final ExampleQuestionService exampleQuestionService;
    private final LearningMemoryService learningMemoryService;
    private final UserService userService;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    private ChatClient normalChatClient;
    private ChatClient learningChatClient;

    @PostConstruct
    void init() {
        var memoryAdvisor = new BranchingChatMemoryAdvisor(chatMemory);
        this.normalChatClient = ChatClient
                .builder(chatModel)
                .defaultSystem(UNIFIED_SYSTEM_MESSAGE)
                .defaultAdvisors(memoryAdvisor)
                .build();
        this.learningChatClient = ChatClient
                .builder(chatModel)
                .defaultSystem(LEARNING_SYSTEM_MESSAGE)
                .defaultAdvisors(memoryAdvisor)
                .build();
    }

    private ChatClient clientFor(ChatMode mode) {
        return mode == ChatMode.LEARNING ?
                learningChatClient :
                normalChatClient;
    }

    private static ChatMode resolveMode(@Nullable ChatMode mode) {
        return mode != null ?
                mode :
                ChatMode.NORMAL;
    }

    private ChatClient.ChatClientRequestSpec buildChatPrompt(
            UUID notebookId,
            ChatMode mode,
            String conversationId,
            @Nullable Long historyParentId,
            Object[] toolObjects,
            List<SourceResponseDTO> scopedSources,
            String userInput,
            @Nullable String responseDetail,
            @Nullable String customInstructions
    ) {
        String baseSystem = mode == ChatMode.LEARNING ?
                LEARNING_SYSTEM_MESSAGE :
                UNIFIED_SYSTEM_MESSAGE;

        StringBuilder systemBuilder = new StringBuilder(baseSystem);

        if (!scopedSources.isEmpty()) {
            systemBuilder.append("\n\nAVAILABLE SOURCES IN SCOPE (").append(scopedSources.size()).append("):\n");
            int limit = Math.min(scopedSources.size(), MAX_MANIFEST_SOURCES);
            for (int i = 0; i < limit; i++) {
                SourceResponseDTO s = scopedSources.get(i);
                systemBuilder
                        .append("- ID: ")
                        .append(s.id())
                        .append(" | Title: \"")
                        .append(s.title() != null ?
                                s.title() :
                                "Untitled")
                        .append("\" | Type: ")
                        .append(s.type())
                        .append("\n");
            }
            if (scopedSources.size() > MAX_MANIFEST_SOURCES) {
                systemBuilder
                        .append("- ... and ")
                        .append(scopedSources.size() - MAX_MANIFEST_SOURCES)
                        .append(" more sources\n");
            }
        }

        if (mode == ChatMode.LEARNING) {
            String learningHint = learningMemoryService.buildTutorMemoryHint(notebookId);
            if (!learningHint.isEmpty()) {
                systemBuilder.append("\n\n").append(learningHint);
            }
        }

        String preferenceSuffix = buildPreferenceSuffix(
                responseDetail,
                customInstructions,
                mode == ChatMode.NORMAL ?
                        learningMemoryService.buildNormalModeGuidance(notebookId) :
                        ""
        );
        if (!preferenceSuffix.isEmpty()) {
            systemBuilder.append("\n\nUSER PREFERENCES:\n").append(preferenceSuffix);
        }

        return clientFor(mode).prompt().system(systemBuilder.toString()).tools(toolObjects).advisors(advisorSpec -> {
            advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId);
            advisorSpec.param(BranchingChatMemoryAdvisor.USE_EXPLICIT_PARENT, Boolean.TRUE);
            if (historyParentId != null) {
                advisorSpec.param(BranchingChatMemoryAdvisor.PARENT_MESSAGE_ID, historyParentId);
            }
        }).user(userInput);
    }

    private Object[] buildToolObjects(
            UUID notebookId,
            ChatMode mode,
            NotebookAgentTools agentTools,
            AgentTraceRecorder traceRecorder
    ) {
        List<Object> tools = new ArrayList<>(3);
        tools.add(agentTools);
        if (learningMemoryService.isEnabled(notebookId)) {
            tools.add(new LearningMemoryReadTools(notebookId, learningMemoryService, traceRecorder));
            if (mode == ChatMode.LEARNING) {
                tools.add(new LearningMemoryAgentTools(notebookId, learningMemoryService, traceRecorder));
            }
        }
        return tools.toArray();
    }

    private void recordLearningTurnQuietly(UUID notebookId, ChatMode mode) {
        if (mode != ChatMode.LEARNING) {
            return;
        }
        try {
            learningMemoryService.recordLearningTurn(notebookId);
        } catch (Exception e) {
            log.warn("Could not record learning turn for notebook {}: {}", notebookId, e.getMessage());
        }
    }

    private List<SourceResponseDTO> resolveScopedSources(UUID notebookId, @Nullable List<UUID> selectedSourceIds) {
        List<SourceResponseDTO> all = sourceRepository.findSummariesByNotebookId(notebookId);
        if (all.isEmpty()) {
            return List.of();
        }
        Set<UUID> selected = selectedSourceIds == null || selectedSourceIds.isEmpty() ?
                null :
                new HashSet<>(selectedSourceIds);
        return all
                .stream()
                .filter(s -> s.status() == SourceStatus.READY)
                .filter(s -> selected == null || selected.contains(s.id()))
                .toList();
    }

    private static String buildPreferenceSuffix(
            @Nullable String responseDetail,
            @Nullable String customInstructions,
            @Nullable String learningGuidance
    ) {
        List<String> directives = new ArrayList<>();

        if (responseDetail != null && !responseDetail.isBlank()) {
            switch (responseDetail.trim().toLowerCase(Locale.ROOT)) {
                case "concise" -> directives.add("- Keep your response concise and focused on the essential points.");
                case "detailed" -> directives.add(
                        "- Provide a thorough, detailed explanation with supporting context from the sources.");
                default -> {
                }
            }
        }

        if (learningGuidance != null && !learningGuidance.isBlank()) {
            directives.add(learningGuidance.trim());
        }

        if (customInstructions != null && !customInstructions.isBlank()) {
            directives.add("- Additional instructions (do not override grounding or citation rules): " + customInstructions.trim());
        }

        return String.join("\n", directives);
    }

    @PostMapping("summary")
    ResponseEntity<JobStartedResponse> generateSummary(@PathVariable UUID notebookId) {
        rateLimitService.checkChatRequest(userService.getUserId());
        if (!notebookRepository.existsById(notebookId)) {
            return ResponseEntity.notFound().build();
        }
        notebookRepository.updateSummary(notebookId, null, null);

        var job = sourceJobService.startNotebookSummaryJob(notebookId);
        return ResponseEntity
                .accepted()
                .body(new JobStartedResponse(
                        job.getId(),
                        "Summary generation started. It will be published via SSE when ready."
                ));
    }

    private List<String> deserializeTopics(String topicsJson) {
        if (topicsJson == null || topicsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(topicsJson, STRING_LIST);
        } catch (Exception e) {
            log.error("Error deserializing notebook summary topics", e);
            return List.of();
        }
    }

    @GetMapping("summary")
    ResponseEntity<NotebookSummaryDTO> getSummary(@PathVariable UUID notebookId) {
        var notebook = notebookRepository.findById(notebookId).orElse(null);

        if (notebook == null) {
            return ResponseEntity.notFound().build();
        }

        String summary = notebook.getSummary();
        if (summary == null || summary.trim().isEmpty()) {
            return ResponseEntity.ok(new NotebookSummaryDTO("", List.of()));
        }

        return ResponseEntity.ok(new NotebookSummaryDTO(summary, deserializeTopics(notebook.getSummaryTopics())));
    }

    @DeleteMapping("summary")
    ResponseEntity<Void> deleteSummary(@PathVariable UUID notebookId) {
        var notebook = notebookRepository.findById(notebookId).orElse(null);

        if (notebook == null) {
            return ResponseEntity.notFound().build();
        }

        notebookRepository.updateSummary(notebookId, null, null);
        notebookContextService.evict();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("example-questions")
    public ResponseEntity<ExampleQuestions> getExampleQuestions(@PathVariable UUID notebookId) {
        rateLimitService.checkChatRequest(userService.getUserId());
        return ResponseEntity.ok(new ExampleQuestions(exampleQuestionService.getExampleQuestions(notebookId)));
    }

    @PostMapping("chat")
    ResponseEntity<ChatReplyMessage> generation(
            @PathVariable UUID notebookId,
            @Valid @RequestBody ChatRequest request
    ) {
        rateLimitService.checkChatRequest(userService.getUserId());

        var notebook = notebookRepository.findById(notebookId).orElse(null);
        if (notebook == null) {
            return ResponseEntity.notFound().build();
        }
        ChatMode mode = resolveMode(request.mode());
        String conversationId = request.conversationId() != null ?
                request.conversationId() :
                UUID.randomUUID().toString();

        Conversation conversation;
        if (request.conversationId() != null) {
            conversation = conversationRepository
                    .findByIdAndNotebookId(conversationId, notebookId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
            conversation.setNotebook(notebook);
        } else {
            conversation = new Conversation();
            conversation.setId(conversationId);
            conversation.setNotebook(notebook);
            conversation.setTitle(normalizeTitle(null, request.userInput()));
        }

        List<SourceResponseDTO> scopedSources = resolveScopedSources(notebookId, request.selectedSourceIds());
        AgentTraceRecorder traceRecorder = new AgentTraceRecorder(null, null);

        NotebookAgentTools agentTools = new NotebookAgentTools(
                notebookId,
                request.selectedSourceIds(),
                retrievalService,
                sourceRepository,
                sourceChunkRepository,
                notebookRepository,
                notebookContextService,
                sourceGroupRepository,
                traceRecorder
        );

        Long parentId = request.conversationId() != null ?
                chatMessageService.getActiveLeafId(conversationId) :
                null;

        Object[] toolObjects = buildToolObjects(notebookId, mode, agentTools, traceRecorder);

        var chatResponse = buildChatPrompt(
                notebookId,
                mode,
                conversationId,
                parentId,
                toolObjects,
                scopedSources,
                request.userInput(),
                request.responseDetail(),
                request.customInstructions()
        ).call().chatResponse();

        if (chatResponse == null || chatResponse.getResult() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI provider returned an empty response");
        }

        var output = chatResponse.getResult().getOutput();
        Object reasoningObj = output.getMetadata().get("reasoningContent");
        if (reasoningObj instanceof String reasoning && !reasoning.isEmpty()) {
            traceRecorder.appendThought(reasoning);
        }

        StringBuilder answerBuilder = new StringBuilder();
        ThinkTagStreamParser parser = new ThinkTagStreamParser(
                traceRecorder::appendThought, delta -> {
            traceRecorder.appendAnswer(delta);
            answerBuilder.append(delta);
        }
        );
        parser.accept(output.getText());
        parser.flush();
        traceRecorder.complete();

        conversation.setMode(mode);
        conversationRepository.save(conversation);
        notebookRepository.touchLastActivity(notebookId);

        List<ChatReplyMessage.CitedSource> citedSources = toCitedSources(agentTools.getCollectedDocuments());
        String assistantContent = answerBuilder.toString();
        List<AgentStep> thinkingSteps = traceRecorder.getStepsSnapshot();
        long thinkingDurationMs = traceRecorder.getTotalDurationMs();

        chatMessageService.saveConversationTurn(
                conversation,
                parentId,
                request.userInput(),
                request.selectedSourceIds(),
                assistantContent,
                citedSources,
                thinkingSteps,
                thinkingDurationMs
        );
        recordLearningTurnQuietly(notebookId, mode);

        return ResponseEntity.ok(new ChatReplyMessage(
                assistantContent,
                conversationId,
                conversation.getTitle(),
                citedSources,
                thinkingSteps,
                thinkingDurationMs
        ));
    }

    @PostMapping(value = "chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@PathVariable UUID notebookId, @Valid @RequestBody ChatRequest request) {
        rateLimitService.checkChatRequest(userService.getUserId());

        var notebook = notebookRepository.findById(notebookId).orElse(null);
        if (notebook == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notebook not found");
        }
        UUID ownerId = notebookRepository.findOwnerId(notebookId);
        ChatMode mode = resolveMode(request.mode());
        String conversationId = request.conversationId() != null ?
                request.conversationId() :
                UUID.randomUUID().toString();

        Conversation existingConversation = null;
        if (request.conversationId() != null) {
            existingConversation = conversationRepository
                    .findByIdAndNotebookId(conversationId, notebookId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        }
        boolean isNewConversation = existingConversation == null;

        Conversation conversation = isNewConversation ?
                new Conversation() :
                existingConversation;
        if (isNewConversation) {
            conversation.setId(conversationId);
            conversation.setTitle(normalizeTitle(null, request.userInput()));
        }

        Long parentId = isNewConversation ?
                null :
                chatMessageService.getActiveLeafId(conversationId);

        return executeStreamChat(
                notebook,
                ownerId,
                conversation,
                isNewConversation,
                parentId,
                null,
                request.userInput(),
                request.selectedSourceIds(),
                mode,
                request.responseDetail(),
                request.customInstructions()
        );
    }

    private record MessageStreamContext(
            Notebook notebook,
            @Nullable
            UUID ownerId,
            Conversation conversation,
            ChatMessage targetMessage
    ) {
    }

    private MessageStreamContext loadMessageStreamContext(UUID notebookId, String conversationId, Long messageId) {
        rateLimitService.checkChatRequest(userService.getUserId());

        Notebook notebook = notebookRepository
                .findById(notebookId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notebook not found"));
        UUID ownerId = notebookRepository.findOwnerId(notebookId);

        Conversation conversation = conversationRepository
                .findByIdAndNotebookId(conversationId, notebookId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        ChatMessage targetMessage = chatMessageService
                .getMessage(conversationId, messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        return new MessageStreamContext(notebook, ownerId, conversation, targetMessage);
    }

    private List<UUID> resolveMessageSelectedSourceIds(
            @Nullable List<UUID> requestedSourceIds,
            ChatMessage fallbackMessage
    ) {
        return requestedSourceIds != null && !requestedSourceIds.isEmpty() ?
                requestedSourceIds :
                chatMessageService.deserializeSelectedSourceUuids(fallbackMessage);
    }

    private static ChatMode resolveConversationMode(@Nullable ChatMode requestedMode, Conversation conversation) {
        return requestedMode != null ?
                requestedMode :
                resolveMode(conversation.getMode());
    }

    @PutMapping(value = "conversations/{conversationId}/messages/{messageId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter editMessageStream(
            @PathVariable UUID notebookId,
            @PathVariable String conversationId,
            @PathVariable Long messageId,
            @Valid @RequestBody EditMessageRequest request
    ) {
        MessageStreamContext ctx = loadMessageStreamContext(notebookId, conversationId, messageId);
        if (!"USER".equals(ctx.targetMessage().getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only user messages can be edited");
        }

        List<UUID> effectiveSelectedSourceIds = resolveMessageSelectedSourceIds(
                request.selectedSourceIds(),
                ctx.targetMessage()
        );
        ChatMode mode = resolveConversationMode(request.mode(), ctx.conversation());

        return executeStreamChat(
                ctx.notebook(),
                ctx.ownerId(),
                ctx.conversation(),
                false,
                ctx.targetMessage().getParentId(),
                null,
                request.userInput().trim(),
                effectiveSelectedSourceIds,
                mode,
                request.responseDetail(),
                request.customInstructions()
        );
    }

    @PostMapping(value = "conversations/{conversationId}/messages/{messageId}/regenerate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerateMessageStream(
            @PathVariable UUID notebookId,
            @PathVariable String conversationId,
            @PathVariable Long messageId,
            @Valid @RequestBody(required = false) RegenerateMessageRequest request
    ) {
        MessageStreamContext ctx = loadMessageStreamContext(notebookId, conversationId, messageId);
        ChatMessage targetMessage = ctx.targetMessage();

        ChatMessage userMessage;
        if ("USER".equals(targetMessage.getRole())) {
            userMessage = targetMessage;
        } else if ("ASSISTANT".equals(targetMessage.getRole())) {
            userMessage = chatMessageService
                    .getParentUserMessage(conversationId, targetMessage)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "No preceding user message found to regenerate"
                    ));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported message role");
        }

        List<UUID> effectiveSelectedSourceIds = resolveMessageSelectedSourceIds(
                request != null ?
                        request.selectedSourceIds() :
                        null, userMessage
        );
        ChatMode mode = resolveConversationMode(
                request != null ?
                        request.mode() :
                        null, ctx.conversation()
        );
        String responseDetail = request != null ?
                request.responseDetail() :
                null;
        String customInstructions = request != null ?
                request.customInstructions() :
                null;

        return executeStreamChat(
                ctx.notebook(),
                ctx.ownerId(),
                ctx.conversation(),
                false,
                userMessage.getParentId(),
                userMessage.getId(),
                userMessage.getContent(),
                effectiveSelectedSourceIds,
                mode,
                responseDetail,
                customInstructions
        );
    }

    private SseEmitter executeStreamChat(
            Notebook notebook,
            @Nullable UUID ownerId,
            Conversation conversation,
            boolean isNewConversation,
            @Nullable Long parentId,
            @Nullable Long existingUserMessageId,
            String userInput,
            @Nullable List<UUID> selectedSourceIds,
            ChatMode mode,
            @Nullable String responseDetail,
            @Nullable String customInstructions
    ) {
        UUID notebookId = notebook.getId();
        String conversationId = conversation.getId();

        StreamSink sink = new StreamSink(new SseEmitter(300_000L));
        AtomicBoolean terminal = new AtomicBoolean(false);
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();

        sink.emitter().onCompletion(() -> {
            sink.markInactive();
            disposeQuietly(subscriptionRef.get());
        });
        sink.emitter().onTimeout(() -> {
            sink.markInactive();
            disposeQuietly(subscriptionRef.get());
            sink.completeQuietly();
        });
        sink.emitter().onError(_ -> {
            sink.markInactive();
            disposeQuietly(subscriptionRef.get());
        });

        conversation.setNotebook(notebook);
        conversation.setMode(mode);
        conversation.setStatus(ConversationStatus.GENERATING);
        conversation.setHasUnseenReply(false);
        conversation = conversationRepository.save(conversation);
        notebookRepository.touchLastActivity(notebookId);

        publishConversationUpdated(ownerId, notebookId, conversation);

        sink.send(SseEmitter.event().name("conversation").data(Map.of("conversationId", conversationId)));

        AgentTraceRecorder traceRecorder = new AgentTraceRecorder(
                step -> sink.send(SseEmitter.event().name("step").data(step)),
                (stepId, delta) -> sink.send(SseEmitter
                        .event()
                        .name("thought")
                        .data(Map.of("stepId", stepId, "delta", delta)))
        );

        List<SourceResponseDTO> scopedSources = resolveScopedSources(notebookId, selectedSourceIds);

        NotebookAgentTools agentTools = new NotebookAgentTools(
                notebookId,
                selectedSourceIds,
                retrievalService,
                sourceRepository,
                sourceChunkRepository,
                notebookRepository,
                notebookContextService,
                sourceGroupRepository,
                traceRecorder
        );

        StringBuffer accumulatedText = new StringBuffer();
        ThinkTagStreamParser thinkParser = new ThinkTagStreamParser(
                traceRecorder::appendThought, answerDelta -> {
            if (answerDelta == null || answerDelta.isEmpty()) {
                return;
            }
            traceRecorder.appendAnswer(answerDelta);
            accumulatedText.append(answerDelta);
            boolean sent = sink.send(SseEmitter.event().name("token").data(answerDelta));
            if (!sent) {
                disposeQuietly(subscriptionRef.get());
            }
        }
        );

        final Conversation currentConversation = conversation;
        Object[] toolObjects = buildToolObjects(notebookId, mode, agentTools, traceRecorder);
        var streamResponse = buildChatPrompt(
                notebookId,
                mode,
                conversationId,
                parentId,
                toolObjects,
                scopedSources,
                userInput,
                responseDetail,
                customInstructions
        ).stream().chatResponse();

        Disposable subscription = streamResponse.doOnNext(chatResponse -> {
            if (chatResponse.getResult() == null) {
                return;
            }
            var output = chatResponse.getResult().getOutput();
            Object reasoningObj = output.getMetadata().get("reasoningContent");
            if (reasoningObj instanceof String reasoning && !reasoning.isEmpty()) {
                traceRecorder.appendThought(reasoning);
            }
            String chunk = output.getText();
            if (chunk != null && !chunk.isEmpty()) {
                thinkParser.accept(chunk);
            }
        }).doOnError(error -> Schedulers.boundedElastic().schedule(() -> {
            if (terminal.compareAndSet(false, true)) {
                handleStreamError(
                        sink,
                        ownerId,
                        notebookId,
                        conversationId,
                        parentId,
                        existingUserMessageId,
                        userInput,
                        selectedSourceIds,
                        existingUserMessageId == null,
                        error
                );
            }
        })).doOnComplete(() -> Schedulers.boundedElastic().schedule(() -> {
            thinkParser.flush();
            traceRecorder.complete();
            finalizeStream(
                    terminal,
                    sink,
                    ownerId,
                    notebookId,
                    parentId,
                    existingUserMessageId,
                    userInput,
                    selectedSourceIds,
                    conversationId,
                    isNewConversation,
                    agentTools.getCollectedDocuments(),
                    accumulatedText.toString(),
                    traceRecorder.getStepsSnapshot(),
                    traceRecorder.getTotalDurationMs(),
                    mode
            );
        })).doFinally(signal -> {
            if (signal == reactor.core.publisher.SignalType.CANCEL) {
                Schedulers.boundedElastic().schedule(() -> {
                    thinkParser.flush();
                    traceRecorder.complete();
                    handleStreamCancelled(
                            terminal,
                            sink,
                            ownerId,
                            notebookId,
                            conversationId,
                            currentConversation,
                            parentId,
                            existingUserMessageId,
                            userInput,
                            selectedSourceIds,
                            agentTools.getCollectedDocuments(),
                            accumulatedText.toString(),
                            traceRecorder.getStepsSnapshot(),
                            traceRecorder.getTotalDurationMs(),
                            mode
                    );
                });
            }
        }).subscribe();

        subscriptionRef.set(subscription);
        return sink.emitter();
    }

    private List<ChatReplyMessage.CitedSource> toCitedSources(List<Document> documents) {
        return documents.stream().map(doc -> new ChatReplyMessage.CitedSource(
                doc.getId(),
                String.valueOf(doc.getMetadata().get("sourceId")),
                String.valueOf(doc.getMetadata().get("title")),
                doc.getText()
        )).toList();
    }

    private void finalizeStream(
            AtomicBoolean terminal,
            StreamSink sink,
            @Nullable UUID ownerId,
            UUID notebookId,
            @Nullable Long parentId,
            @Nullable Long existingUserMessageId,
            String userInput,
            List<UUID> selectedSourceIds,
            String conversationId,
            boolean isNewConversation,
            List<Document> retrievedDocuments,
            String assistantContent,
            List<AgentStep> thinkingSteps,
            long thinkingDurationMs,
            ChatMode mode
    ) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }

        try {
            Conversation conversation = conversationRepository
                    .findByIdAndNotebookId(conversationId, notebookId)
                    .orElse(null);
            if (conversation == null) {
                return;
            }
            if (isNewConversation) {
                conversation.setTitle(normalizeTitle(conversation.getTitle(), userInput));
            }
            conversation.setMode(mode);
            conversation.setStatus(ConversationStatus.IDLE);
            conversation.setHasUnseenReply(!sink.isActive());
            conversation = conversationRepository.save(conversation);
            notebookRepository.touchLastActivity(notebookId);

            List<ChatReplyMessage.CitedSource> citedSources = toCitedSources(retrievedDocuments);
            ChatMessageService.SavedTurn savedTurn = existingUserMessageId != null ?
                    chatMessageService.saveRegeneratedAssistant(
                            conversation,
                            existingUserMessageId,
                            assistantContent,
                            citedSources,
                            thinkingSteps,
                            thinkingDurationMs
                    ) :
                    chatMessageService.saveConversationTurn(
                            conversation,
                            parentId,
                            userInput,
                            selectedSourceIds,
                            assistantContent,
                            citedSources,
                            thinkingSteps,
                            thinkingDurationMs
                    );

            sink.send(SseEmitter.event().name("sources").data(citedSources));
            if (isNewConversation) {
                sink.send(SseEmitter.event().name("title").data(conversation.getTitle()));
            }
            Map<String, Object> donePayload = new LinkedHashMap<>();
            donePayload.put("durationMs", thinkingDurationMs);
            donePayload.put("userMessageId", savedTurn.userMessageId());
            donePayload.put("assistantMessageId", savedTurn.assistantMessageId());
            donePayload.put("userSiblingIds", savedTurn.userSiblingIds());
            donePayload.put("assistantSiblingIds", savedTurn.assistantSiblingIds());
            boolean sentDone = sink.send(SseEmitter.event().name("done").data(donePayload));
            if (!sentDone && !conversation.isHasUnseenReply()) {
                conversation.setHasUnseenReply(true);
                conversation = conversationRepository.save(conversation);
            }
            sink.complete();

            recordLearningTurnQuietly(notebookId, mode);
            publishConversationUpdated(ownerId, notebookId, conversation);
        } catch (Exception e) {
            log.error("Error finalizing chat stream for conversation {}", conversationId, e);
            handleStreamError(
                    sink,
                    ownerId,
                    notebookId,
                    conversationId,
                    parentId,
                    existingUserMessageId,
                    userInput,
                    selectedSourceIds,
                    false,
                    e
            );
        }
    }

    private void handleStreamError(
            StreamSink sink,
            @Nullable UUID ownerId,
            UUID notebookId,
            String conversationId,
            @Nullable Long parentId,
            @Nullable Long existingUserMessageId,
            String userInput,
            List<UUID> selectedSourceIds,
            boolean persistUserMessage,
            Throwable error
    ) {
        try {
            Conversation conversation = conversationRepository
                    .findByIdAndNotebookId(conversationId, notebookId)
                    .orElse(null);
            if (conversation != null) {
                if (persistUserMessage && existingUserMessageId == null) {
                    ChatMessage savedUser = chatMessageService.saveUserMessage(
                            conversation,
                            parentId,
                            userInput,
                            selectedSourceIds
                    );
                    if (savedUser.getId() != null) {
                        List<Long> siblingIds = chatMessageService.getSiblingIds(conversationId, parentId);
                        sink.send(SseEmitter
                                .event()
                                .name("userMessage")
                                .data(Map.of("id", savedUser.getId(), "siblingIds", siblingIds)));
                    }
                }
                conversation.setStatus(ConversationStatus.FAILED);
                conversation.setHasUnseenReply(false);
                conversation = conversationRepository.save(conversation);
                notebookRepository.touchLastActivity(notebookId);
                publishConversationUpdated(ownerId, notebookId, conversation);
            }
        } catch (Exception e) {
            log.warn("Could not record failure for conversation {}: {}", conversationId, e.getMessage());
        }
        sendErrorAndComplete(sink, error);
    }

    private void handleStreamCancelled(
            AtomicBoolean terminal,
            StreamSink sink,
            @Nullable UUID ownerId,
            UUID notebookId,
            String conversationId,
            Conversation currentConversation,
            @Nullable Long parentId,
            @Nullable Long existingUserMessageId,
            String userInput,
            List<UUID> selectedSourceIds,
            List<Document> retrievedDocuments,
            String assistantContent,
            List<AgentStep> thinkingSteps,
            long thinkingDurationMs,
            ChatMode mode
    ) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }

        try {
            Conversation conversation = conversationRepository
                    .findByIdAndNotebookId(conversationId, notebookId)
                    .orElse(currentConversation);
            conversation.setMode(mode);
            conversation.setStatus(ConversationStatus.IDLE);
            conversation.setHasUnseenReply(true);
            conversation = conversationRepository.save(conversation);
            notebookRepository.touchLastActivity(notebookId);

            boolean hasAssistantContent = assistantContent != null && !assistantContent.isBlank();
            if (existingUserMessageId != null) {
                if (hasAssistantContent) {
                    chatMessageService.saveRegeneratedAssistant(
                            conversation,
                            existingUserMessageId,
                            assistantContent,
                            toCitedSources(retrievedDocuments),
                            thinkingSteps,
                            thinkingDurationMs
                    );
                }
            } else if (hasAssistantContent) {
                chatMessageService.saveConversationTurn(
                        conversation,
                        parentId,
                        userInput,
                        selectedSourceIds,
                        assistantContent,
                        toCitedSources(retrievedDocuments),
                        thinkingSteps,
                        thinkingDurationMs
                );
            } else {
                chatMessageService.saveUserMessage(conversation, parentId, userInput, selectedSourceIds);
            }

            sink.markInactive();
            sink.completeQuietly();
            publishConversationUpdated(ownerId, notebookId, conversation);
        } catch (Exception e) {
            log.warn("Could not record cancellation for conversation {}: {}", conversationId, e.getMessage());
        }
    }

    private void publishConversationUpdated(@Nullable UUID ownerId, UUID notebookId, Conversation conversation) {
        if (ownerId == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notebookId", notebookId.toString());
        payload.put("conversationId", conversation.getId());
        payload.put("title", conversation.getTitle());
        payload.put("mode", conversation.getMode().toString());
        payload.put("status", conversation.getStatus().toString());
        payload.put("hasUnseenReply", conversation.isHasUnseenReply());
        payload.put(
                "createdAt",
                conversation.getCreatedAt() != null ?
                        conversation.getCreatedAt().toInstant().toString() :
                        null
        );
        payload.put(
                "updatedAt",
                conversation.getUpdatedAt() != null ?
                        conversation.getUpdatedAt().toInstant().toString() :
                        null
        );
        sseNotificationService.publish(ownerId, "conversation.updated", payload);
    }

    private void disposeQuietly(@Nullable Disposable disposable) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    private String normalizeTitle(@Nullable String title, String fallback) {
        if (title == null || title.isBlank()) {
            title = generateTitle(fallback);
        }
        title = title.replaceAll("^[\"']|[\"']$", "").trim();
        if (title.length() > 100) {
            title = title.substring(0, 97) + "...";
        }
        return title;
    }

    /**
     * Inexpensive, deterministic title generation. The previous implementation made an
     * extra blocking LLM call for every new conversation.
     */
    private String generateTitle(String userInput) {
        String cleaned = userInput == null ?
                "" :
                userInput.strip().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return "New conversation";
        }
        int end = Math.min(cleaned.length(), 60);
        int lastSpace = cleaned.lastIndexOf(' ', end);
        if (lastSpace > 20) {
            end = lastSpace;
        }
        return cleaned.substring(0, end).trim();
    }

    private void sendErrorAndComplete(StreamSink sink, Throwable error) {
        String message = error.getMessage() != null ?
                error.getMessage() :
                "Error generating response";
        sink.send(SseEmitter.event().name("error").data(message));
        sink.complete();
        sink.completeQuietly();
    }

    private static final class StreamSink {
        private final SseEmitter emitter;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final ReentrantLock lock = new ReentrantLock();

        private StreamSink(SseEmitter emitter) {
            this.emitter = emitter;
        }

        private SseEmitter emitter() {
            return emitter;
        }

        private boolean isActive() {
            return active.get();
        }

        private void markInactive() {
            active.set(false);
        }

        private boolean send(SseEmitter.SseEventBuilder event) {
            if (!active.get()) {
                return false;
            }
            lock.lock();
            try {
                if (!active.get()) {
                    return false;
                }
                emitter.send(event);
                return true;
            } catch (Exception e) {
                active.set(false);
                return false;
            } finally {
                lock.unlock();
            }
        }

        private void complete() {
            if (!active.getAndSet(false)) {
                return;
            }
            completeQuietly();
        }

        private void completeQuietly() {
            lock.lock();
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
            } finally {
                lock.unlock();
            }
        }
    }
}
