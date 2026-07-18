package com.escruta.core.services;

import com.escruta.core.controllers.ChatController;
import com.escruta.core.entities.ChatMessage;
import com.escruta.core.repositories.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link ChatMemory} backed by the application's {@code messages} table.
 *
 * <p>It provides a sliding window of the most recent messages for LLM context
 * ({@link #get(String)}) while the full conversation history is persisted by
 * {@link ChatController} through {@link ChatMessageService}. The {@link #add}
 * method is intentionally a no-op: messages are stored explicitly by the
 * controller alongside their metadata (cited sources and selected sources),
 * which the memory advisor cannot convey.
 */
@Component
@RequiredArgsConstructor
public class JpaChatMemory implements ChatMemory {
    private static final int DEFAULT_MAX_MESSAGES = 20;

    private final ChatMessageRepository chatMessageRepository;

    @Override
    public void add(@NonNull String conversationId, @NonNull List<Message> messages) {
        // No-op: messages are persisted by ChatController via ChatMessageService.
    }

    @Override
    public @NonNull List<Message> get(@NonNull String conversationId) {
        return get(conversationId, DEFAULT_MAX_MESSAGES);
    }

    public List<Message> get(String conversationId, int maxMessages) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }

        List<ChatMessage> recent = chatMessageRepository.findByConversation_IdOrderByIdDesc(
                conversationId,
                PageRequest.of(0, maxMessages)
        );
        if (recent.isEmpty()) {
            return List.of();
        }

        Collections.reverse(recent);
        List<Message> messages = new ArrayList<>(recent.size());
        for (ChatMessage message : recent) {
            messages.add(toAiMessage(message));
        }
        return messages;
    }

    @Override
    public void clear(@NonNull String conversationId) {
        chatMessageRepository.deleteByConversation_Id(conversationId);
    }

    private Message toAiMessage(ChatMessage message) {
        return "ASSISTANT".equals(message.getRole()) ?
                new AssistantMessage(message.getContent()) :
                new UserMessage(message.getContent());
    }
}
