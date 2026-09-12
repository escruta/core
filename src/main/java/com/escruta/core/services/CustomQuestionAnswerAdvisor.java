package com.escruta.core.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.lang.NonNull;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public class CustomQuestionAnswerAdvisor implements BaseAdvisor {
    public static final String RETRIEVED_DOCUMENTS = "qa_retrieved_documents";
    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate("""
            {query}
            
            Context information is below, surrounded by ---------------------
            
            ---------------------
            {question_answer_context}
            ---------------------
            
            Given the context and provided history information and not prior knowledge,
            reply to the user comment. If the answer is not in the context, inform
            the user that you can't answer the question.
            """);

    private static final int DEFAULT_ORDER = 0;
    private static final int DEFAULT_TOP_K = 5;
    private final RetrievalService retrievalService;
    private final UUID notebookId;
    private final List<UUID> selectedSourceIds;
    private final int topK;
    private final PromptTemplate promptTemplate;
    private final Scheduler scheduler;
    private final int order;

    CustomQuestionAnswerAdvisor(
            RetrievalService retrievalService,
            UUID notebookId,
            @Nullable List<UUID> selectedSourceIds,
            int topK,
            @Nullable PromptTemplate promptTemplate,
            @Nullable Scheduler scheduler,
            int order
    ) {
        Assert.notNull(retrievalService, "retrievalService cannot be null");
        Assert.notNull(notebookId, "notebookId cannot be null");

        this.retrievalService = retrievalService;
        this.notebookId = notebookId;
        this.selectedSourceIds = selectedSourceIds;
        this.topK = topK;
        this.promptTemplate = promptTemplate != null ?
                promptTemplate :
                DEFAULT_PROMPT_TEMPLATE;
        this.scheduler = scheduler != null ?
                scheduler :
                BaseAdvisor.DEFAULT_SCHEDULER;
        this.order = order;
    }

    public static Builder builder(RetrievalService retrievalService, UUID notebookId) {
        return new Builder(retrievalService, notebookId);
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, @NonNull AdvisorChain advisorChain) {
        String query = chatClientRequest.prompt().getUserMessage().getText();

        List<Document> documents = this.retrievalService.search(
                this.notebookId,
                this.selectedSourceIds,
                query,
                this.topK
        );

        Map<String, Object> context = new HashMap<>(chatClientRequest.context());
        context.put(RETRIEVED_DOCUMENTS, documents);

        String documentContext = documents
                .stream()
                .map(doc -> "Source ID: " + doc.getId() + "\\n" + doc.getText())
                .collect(Collectors.joining(System.lineSeparator() + "---------------------" + System.lineSeparator()));

        UserMessage userMessage = chatClientRequest.prompt().getUserMessage();
        String augmentedUserText = this.promptTemplate.render(Map.of(
                "query",
                userMessage.getText(),
                "question_answer_context",
                documentContext
        ));

        return chatClientRequest
                .mutate()
                .prompt(chatClientRequest.prompt().augmentUserMessage(augmentedUserText))
                .context(context)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, @NonNull AdvisorChain advisorChain) {
        ChatResponse.Builder chatResponseBuilder;
        if (chatClientResponse.chatResponse() == null) {
            chatResponseBuilder = ChatResponse.builder();
        } else {
            chatResponseBuilder = ChatResponse.builder().from(chatClientResponse.chatResponse());
        }
        chatResponseBuilder.metadata(RETRIEVED_DOCUMENTS, chatClientResponse.context().get(RETRIEVED_DOCUMENTS));
        return ChatClientResponse
                .builder()
                .chatResponse(chatResponseBuilder.build())
                .context(chatClientResponse.context())
                .build();
    }

    @Override
    public Scheduler getScheduler() {
        return this.scheduler;
    }

    public static final class Builder {
        private final RetrievalService retrievalService;
        private final UUID notebookId;
        private List<UUID> selectedSourceIds;
        private int topK = DEFAULT_TOP_K;
        private PromptTemplate promptTemplate;
        private Scheduler scheduler;
        private int order = DEFAULT_ORDER;

        private Builder(RetrievalService retrievalService, UUID notebookId) {
            Assert.notNull(retrievalService, "The retrievalService must not be null!");
            Assert.notNull(notebookId, "The notebookId must not be null!");
            this.retrievalService = retrievalService;
            this.notebookId = notebookId;
        }

        public Builder selectedSourceIds(List<UUID> selectedSourceIds) {
            this.selectedSourceIds = selectedSourceIds;
            return this;
        }

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public Builder promptTemplate(PromptTemplate promptTemplate) {
            Assert.notNull(promptTemplate, "promptTemplate cannot be null");
            this.promptTemplate = promptTemplate;
            return this;
        }

        public Builder protectFromBlocking(boolean protectFromBlocking) {
            this.scheduler = protectFromBlocking ?
                    BaseAdvisor.DEFAULT_SCHEDULER :
                    Schedulers.immediate();
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public CustomQuestionAnswerAdvisor build() {
            return new CustomQuestionAnswerAdvisor(
                    this.retrievalService,
                    this.notebookId,
                    this.selectedSourceIds,
                    this.topK,
                    this.promptTemplate,
                    this.scheduler,
                    this.order
            );
        }

    }

}
