package com.escruta.core.controllers;

import com.escruta.core.dtos.AgentStep;
import com.escruta.core.dtos.ChatReplyMessage;
import com.escruta.core.dtos.ChatRequest;
import com.escruta.core.dtos.ExampleQuestions;
import com.escruta.core.dtos.NotebookSummaryDTO;
import com.escruta.core.dtos.source.SourceResponseDTO;
import com.escruta.core.dtos.tools.JobStartedResponse;
import com.escruta.core.entities.Conversation;
import com.escruta.core.entities.enums.ChatMode;
import com.escruta.core.entities.enums.ConversationStatus;
import com.escruta.core.entities.enums.SourceStatus;
import com.escruta.core.repositories.ConversationRepository;
import com.escruta.core.repositories.NotebookRepository;
import com.escruta.core.repositories.SourceChunkRepository;
import com.escruta.core.repositories.SourceGroupRepository;
import com.escruta.core.repositories.SourceRepository;
import com.escruta.core.services.AgentTraceRecorder;
import com.escruta.core.services.ChatMessageService;
import com.escruta.core.services.ExampleQuestionService;
import com.escruta.core.services.JpaChatMemory;
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
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
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
            - Before answering any question about the sources, ALWAYS use your tools to search or read the relevant sources.
            - Break complex or multi-part questions into focused searches. If a passage seems cut off or incomplete, use `getSurroundingChunks` or `readSourceChunks` to read adjacent sections.
            - Note the distinction in tool outputs: `Source ID: <uuid>` is the passage UUID used in `[source_<uuid>]` citations and `getSurroundingChunks`; `Document ID: <uuid>` is the parent source UUID used in `getSourceOverview`, `readSourceChunks`, and `searchExactMatch`.
            - Never mention internal tool names (such as `searchSources`, `searchExactMatch`, `getSourceOverview`, `readSourceChunks`, `getSurroundingChunks`, `listSources`, `getNotebookOverview`, or `calculate`) in your final answer.
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
            - Before answering any question about the sources, ALWAYS use your tools to search or read the relevant sources.
            - Break complex questions into focused searches and gather accurate evidence before teaching. If a passage seems cut off, use `getSurroundingChunks` or `readSourceChunks` to read the surrounding context.
            - Note the distinction in tool outputs: `Source ID: <uuid>` is the passage UUID used in `[source_<uuid>]` citations and `getSurroundingChunks`; `Document ID: <uuid>` is the parent source UUID used in `getSourceOverview`, `readSourceChunks`, and `searchExactMatch`.
            - Never mention internal tool names (such as `searchSources`, `searchExactMatch`, `getSourceOverview`, `readSourceChunks`, `getSurroundingChunks`, `listSources`, `getNotebookOverview`, or `calculate`) in your final answer.
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
    private final UserService userService;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    private ChatClient normalChatClient;
    private ChatClient learningChatClient;

    @PostConstruct
    void init() {
        var memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
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
            ChatMode mode,
            String conversationId,
            NotebookAgentTools agentTools,
            List<SourceResponseDTO> scopedSources,
            ChatRequest request
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

        String preferenceSuffix = buildPreferenceSuffix(request);
        if (!preferenceSuffix.isEmpty()) {
            systemBuilder.append("\nUSER PREFERENCES:\n").append(preferenceSuffix);
        }

        return clientFor(mode)
                .prompt()
                .system(systemBuilder.toString())
                .tools(agentTools)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(request.userInput());
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

    private static String buildPreferenceSuffix(ChatRequest request) {
        List<String> directives = new ArrayList<>();

        if (request.responseDetail() != null && !request.responseDetail().isBlank()) {
            switch (request.responseDetail().trim().toLowerCase(Locale.ROOT)) {
                case "concise" -> directives.add("- Keep your response concise and focused on the essential points.");
                case "detailed" -> directives.add(
                        "- Provide a thorough, detailed explanation with supporting context from the sources.");
                default -> {
                }
            }
        }

        if (request.customInstructions() != null && !request.customInstructions().isBlank()) {
            directives.add("- Additional instructions (do not override grounding or citation rules): " + request
                    .customInstructions()
                    .trim());
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

        var chatResponse = buildChatPrompt(mode, conversationId, agentTools, scopedSources, request)
                .call()
                .chatResponse();

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
                request.userInput(),
                request.selectedSourceIds(),
                assistantContent,
                citedSources,
                thinkingSteps,
                thinkingDurationMs
        );

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

        Conversation conversation = isNewConversation ?
                new Conversation() :
                existingConversation;
        if (isNewConversation) {
            conversation.setId(conversationId);
            conversation.setTitle(normalizeTitle(null, request.userInput()));
        }
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

        List<SourceResponseDTO> scopedSources = resolveScopedSources(notebookId, request.selectedSourceIds());

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
        var streamResponse = buildChatPrompt(mode, conversationId, agentTools, scopedSources, request)
                .stream()
                .chatResponse();

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
                        request.userInput(),
                        request.selectedSourceIds(),
                        true,
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
                    request.userInput(),
                    request.selectedSourceIds(),
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
                            request.userInput(),
                            request.selectedSourceIds(),
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
            chatMessageService.saveConversationTurn(
                    conversation,
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
            boolean sentDone = sink.send(SseEmitter
                    .event()
                    .name("done")
                    .data(Map.of("durationMs", thinkingDurationMs)));
            if (!sentDone && !conversation.isHasUnseenReply()) {
                conversation.setHasUnseenReply(true);
                conversation = conversationRepository.save(conversation);
            }
            sink.complete();

            publishConversationUpdated(ownerId, notebookId, conversation);
        } catch (Exception e) {
            log.error("Error finalizing chat stream for conversation {}", conversationId, e);
            handleStreamError(sink, ownerId, notebookId, conversationId, userInput, selectedSourceIds, false, e);
        }
    }

    private void handleStreamError(
            StreamSink sink,
            @Nullable UUID ownerId,
            UUID notebookId,
            String conversationId,
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
                if (persistUserMessage) {
                    chatMessageService.saveUserMessage(conversation, userInput, selectedSourceIds);
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

            if (assistantContent != null && !assistantContent.isBlank()) {
                chatMessageService.saveConversationTurn(
                        conversation,
                        userInput,
                        selectedSourceIds,
                        assistantContent,
                        toCitedSources(retrievedDocuments),
                        thinkingSteps,
                        thinkingDurationMs
                );
            } else {
                chatMessageService.saveUserMessage(conversation, userInput, selectedSourceIds);
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
