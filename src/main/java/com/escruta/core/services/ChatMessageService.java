package com.escruta.core.services;

import com.escruta.core.dtos.ChatReplyMessage;
import com.escruta.core.entities.ChatMessage;
import com.escruta.core.entities.Conversation;
import com.escruta.core.repositories.ChatMessageRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<ChatReplyMessage.CitedSource>> CITED_SOURCE_LIST = new TypeReference<>() {
    };
    private static final int MAX_STORED_CITATION_CHARS = 500;

    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveUserMessage(Conversation conversation, String userContent, List<UUID> selectedSourceIds) {
        ChatMessage userMessage = new ChatMessage();
        userMessage.setConversation(conversation);
        userMessage.setRole("USER");
        userMessage.setContent(userContent);
        userMessage.setSelectedSourceIds(toJson(selectedSourceIds == null ?
                null :
                selectedSourceIds.stream().map(UUID::toString).toList()));
        chatMessageRepository.save(userMessage);
    }

    @Transactional
    public void saveAssistantMessage(
            Conversation conversation,
            String assistantContent,
            List<ChatReplyMessage.CitedSource> citedSources
    ) {
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setConversation(conversation);
        assistantMessage.setRole("ASSISTANT");
        assistantMessage.setContent(assistantContent);
        assistantMessage.setCitedSources(toJson(truncateForStorage(citedSources)));
        chatMessageRepository.save(assistantMessage);
    }

    private List<ChatReplyMessage.CitedSource> truncateForStorage(List<ChatReplyMessage.CitedSource> citedSources) {
        if (citedSources == null || citedSources.isEmpty()) {
            return citedSources;
        }
        return citedSources.stream().map(source -> {
            String text = source.text();
            if (text == null || text.length() <= MAX_STORED_CITATION_CHARS) {
                return source;
            }
            return new ChatReplyMessage.CitedSource(
                    source.id(),
                    source.documentId(),
                    source.title(),
                    text.substring(0, MAX_STORED_CITATION_CHARS)
            );
        }).toList();
    }

    @Transactional
    public void saveConversationTurn(
            Conversation conversation,
            String userContent,
            List<UUID> selectedSourceIds,
            String assistantContent,
            List<ChatReplyMessage.CitedSource> citedSources
    ) {
        saveUserMessage(conversation, userContent, selectedSourceIds);
        saveAssistantMessage(conversation, assistantContent, citedSources);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getConversationMessages(String conversationId) {
        return getConversationMessages(conversationId, 1000);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getConversationMessages(String conversationId, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 1000));
        List<ChatMessage> recent = chatMessageRepository.findByConversation_IdOrderByIdDesc(
                conversationId,
                org.springframework.data.domain.PageRequest.of(0, boundedLimit)
        );
        java.util.Collections.reverse(recent);
        return recent;
    }

    @Transactional
    public void deleteByConversationId(String conversationId) {
        chatMessageRepository.deleteAllByConversationId(conversationId);
    }

    public List<String> deserializeSelectedSourceIds(ChatMessage message) {
        return fromJson(message.getSelectedSourceIds(), STRING_LIST, List.of());
    }

    public List<ChatReplyMessage.CitedSource> deserializeCitedSources(ChatMessage message) {
        return fromJson(message.getCitedSources(), CITED_SOURCE_LIST, List.of());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("Error serializing chat message metadata", e);
            return null;
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type, T defaultValue) {
        if (json == null || json.isBlank()) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("Error deserializing chat message metadata", e);
            return defaultValue;
        }
    }
}
