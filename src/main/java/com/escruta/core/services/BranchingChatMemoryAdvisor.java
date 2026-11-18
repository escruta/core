package com.escruta.core.services;

import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.ArrayList;
import java.util.List;

public class BranchingChatMemoryAdvisor implements BaseAdvisor {
    public static final String USE_EXPLICIT_PARENT = "escruta_use_explicit_parent";
    public static final String PARENT_MESSAGE_ID = "escruta_parent_message_id";

    private final JpaChatMemory chatMemory;
    private final int order;

    public BranchingChatMemoryAdvisor(JpaChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        this.order = Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public @NonNull ChatClientRequest before(@NonNull ChatClientRequest request, @NonNull AdvisorChain advisorChain) {
        Object convObj = request.context().get(ChatMemory.CONVERSATION_ID);
        String conversationId = convObj != null ?
                String.valueOf(convObj) :
                null;

        boolean useExplicitParent = Boolean.TRUE.equals(request.context().get(USE_EXPLICIT_PARENT));
        List<Message> memoryMessages;
        if (useExplicitParent) {
            Object parentObj = request.context().get(PARENT_MESSAGE_ID);
            Long parentId = parentObj instanceof Number number ?
                    number.longValue() :
                    null;
            memoryMessages = parentId != null ?
                    chatMemory.getAncestors(conversationId, parentId) :
                    List.of();
        } else {
            assert conversationId != null;
            memoryMessages = chatMemory.get(conversationId);
        }

        List<Message> currentInstructions = request.prompt().getInstructions();
        List<Message> combined = new ArrayList<>(memoryMessages.size() + currentInstructions.size());
        combined.addAll(memoryMessages);
        combined.addAll(currentInstructions);

        for (int i = 0; i < combined.size(); i++) {
            if (combined.get(i) instanceof SystemMessage) {
                Message sys = combined.remove(i);
                combined.addFirst(sys);
                break;
            }
        }

        return request.mutate().prompt(request.prompt().mutate().messages(combined).build()).build();
    }

    @Override
    public @NonNull ChatClientResponse after(@NonNull ChatClientResponse response, @NonNull AdvisorChain advisorChain) {
        return response;
    }
}
