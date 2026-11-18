package com.escruta.core.services;

import com.escruta.core.entities.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JpaChatMemory implements ChatMemory {
    private static final int DEFAULT_MAX_MESSAGES = 20;

    private final ChatMessageService chatMessageService;

    @Override
    public void add(@NonNull String conversationId, @NonNull List<Message> messages) {
    }

    @Override
    public @NonNull List<Message> get(@NonNull String conversationId) {
        return get(conversationId, DEFAULT_MAX_MESSAGES);
    }

    public List<Message> get(String conversationId, int maxMessages) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        List<ChatMessage> recent = chatMessageService.getConversationMessages(conversationId, maxMessages);
        return recent.stream().map(this::toAiMessage).toList();
    }

    public List<Message> getAncestors(String conversationId, @Nullable Long leafMessageId) {
        return getAncestors(conversationId, leafMessageId, DEFAULT_MAX_MESSAGES);
    }

    public List<Message> getAncestors(String conversationId, @Nullable Long leafMessageId, int maxMessages) {
        if (conversationId == null || conversationId.isBlank() || leafMessageId == null) {
            return List.of();
        }
        return chatMessageService
                .getAncestors(conversationId, leafMessageId, maxMessages)
                .stream()
                .map(this::toAiMessage)
                .toList();
    }

    @Override
    public void clear(@NonNull String conversationId) {
        chatMessageService.deleteByConversationId(conversationId);
    }

    private Message toAiMessage(ChatMessage message) {
        return "ASSISTANT".equals(message.getRole()) ?
                new AssistantMessage(message.getContent()) :
                new UserMessage(message.getContent());
    }
}
