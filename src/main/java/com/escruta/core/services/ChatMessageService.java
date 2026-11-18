package com.escruta.core.services;

import com.escruta.core.dtos.AgentStep;
import com.escruta.core.dtos.ChatReplyMessage;
import com.escruta.core.entities.ChatMessage;
import com.escruta.core.entities.Conversation;
import com.escruta.core.repositories.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageService {
    public record SavedTurn(
            Long userMessageId,
            Long assistantMessageId,
            List<Long> userSiblingIds,
            List<Long> assistantSiblingIds
    ) {
    }

    public record ActiveMessageNode(
            ChatMessage message,
            List<Long> siblingIds
    ) {
    }

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<ChatReplyMessage.CitedSource>> CITED_SOURCE_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<AgentStep>> AGENT_STEP_LIST = new TypeReference<>() {
    };
    private static final int MAX_STORED_CITATION_CHARS = 500;

    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ChatMessage saveUserMessage(
            Conversation conversation,
            @Nullable Long parentId,
            String userContent,
            @Nullable List<UUID> selectedSourceIds
    ) {
        deactivateSiblings(conversation.getId(), parentId);
        ChatMessage userMessage = new ChatMessage();
        userMessage.setConversation(conversation);
        userMessage.setParentId(parentId);
        userMessage.setActive(true);
        userMessage.setRole("USER");
        userMessage.setContent(userContent);
        userMessage.setSelectedSourceIds(toJson(selectedSourceIds == null ?
                null :
                selectedSourceIds.stream().map(UUID::toString).toList()));
        return chatMessageRepository.save(userMessage);
    }

    @Transactional
    public ChatMessage saveAssistantMessage(
            Conversation conversation,
            @Nullable Long parentId,
            String assistantContent,
            List<ChatReplyMessage.CitedSource> citedSources,
            List<AgentStep> thinkingSteps,
            Long thinkingDurationMs
    ) {
        deactivateSiblings(conversation.getId(), parentId);
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setConversation(conversation);
        assistantMessage.setParentId(parentId);
        assistantMessage.setActive(true);
        assistantMessage.setRole("ASSISTANT");
        assistantMessage.setContent(assistantContent);
        assistantMessage.setCitedSources(toJson(truncateForStorage(citedSources)));
        if (thinkingSteps != null && !thinkingSteps.isEmpty()) {
            assistantMessage.setThinkingSteps(toJson(thinkingSteps));
        }
        assistantMessage.setThinkingDurationMs(thinkingDurationMs);
        return chatMessageRepository.save(assistantMessage);
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
    public SavedTurn saveConversationTurn(
            Conversation conversation,
            @Nullable Long parentId,
            String userContent,
            @Nullable List<UUID> selectedSourceIds,
            String assistantContent,
            List<ChatReplyMessage.CitedSource> citedSources,
            List<AgentStep> thinkingSteps,
            Long thinkingDurationMs
    ) {
        ChatMessage userMessage = saveUserMessage(conversation, parentId, userContent, selectedSourceIds);
        ChatMessage assistantMessage = saveAssistantMessage(
                conversation,
                userMessage.getId(),
                assistantContent,
                citedSources,
                thinkingSteps,
                thinkingDurationMs
        );
        return new SavedTurn(
                userMessage.getId(),
                assistantMessage.getId(),
                getSiblingIds(conversation.getId(), parentId),
                getSiblingIds(conversation.getId(), userMessage.getId())
        );
    }

    @Transactional
    public SavedTurn saveRegeneratedAssistant(
            Conversation conversation,
            Long userMessageId,
            String assistantContent,
            List<ChatReplyMessage.CitedSource> citedSources,
            List<AgentStep> thinkingSteps,
            Long thinkingDurationMs
    ) {
        ChatMessage userMessage = chatMessageRepository
                .findByIdAndConversation_Id(userMessageId, conversation.getId())
                .orElseThrow(() -> new IllegalStateException("User message not found: " + userMessageId));

        ChatMessage assistantMessage = saveAssistantMessage(
                conversation,
                userMessageId,
                assistantContent,
                citedSources,
                thinkingSteps,
                thinkingDurationMs
        );
        return new SavedTurn(
                userMessageId,
                assistantMessage.getId(),
                getSiblingIds(conversation.getId(), userMessage.getParentId()),
                getSiblingIds(conversation.getId(), userMessageId)
        );
    }

    @Transactional(readOnly = true)
    public Optional<ChatMessage> getMessage(String conversationId, Long messageId) {
        return chatMessageRepository.findByIdAndConversation_Id(messageId, conversationId);
    }

    @Transactional(readOnly = true)
    public Optional<ChatMessage> getParentUserMessage(String conversationId, ChatMessage assistantMessage) {
        if (assistantMessage.getParentId() != null) {
            Optional<ChatMessage> parent = chatMessageRepository.findByIdAndConversation_Id(
                    assistantMessage.getParentId(),
                    conversationId
            );
            if (parent.isPresent() && "USER".equals(parent.get().getRole())) {
                return parent;
            }
        }
        return chatMessageRepository.findFirstByConversation_IdAndRoleAndIdLessThanOrderByIdDesc(
                conversationId,
                "USER",
                assistantMessage.getId()
        );
    }

    @Transactional(readOnly = true)
    public List<ActiveMessageNode> getActiveBranch(String conversationId) {
        List<ChatMessage> all = chatMessageRepository.findByConversation_IdOrderByIdAsc(conversationId);
        if (all.isEmpty()) {
            return List.of();
        }

        List<ChatMessage> roots = new ArrayList<>();
        Map<Long, List<ChatMessage>> childrenByParent = new HashMap<>();
        for (ChatMessage msg : all) {
            if (msg.getParentId() == null) {
                roots.add(msg);
            } else {
                childrenByParent.computeIfAbsent(msg.getParentId(), _ -> new ArrayList<>()).add(msg);
            }
        }

        List<ActiveMessageNode> path = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        List<ChatMessage> currentLevel = roots;

        while (currentLevel != null && !currentLevel.isEmpty()) {
            ChatMessage chosen = null;
            for (ChatMessage candidate : currentLevel) {
                if (candidate.isActive()) {
                    chosen = candidate;
                    break;
                }
            }
            if (chosen == null) {
                chosen = currentLevel.getLast();
            }
            if (!visited.add(chosen.getId())) {
                break;
            }
            List<Long> siblingIds = currentLevel.stream().map(ChatMessage::getId).toList();
            path.add(new ActiveMessageNode(chosen, siblingIds));
            currentLevel = childrenByParent.get(chosen.getId());
        }

        return path;
    }

    @Transactional(readOnly = true)
    public @Nullable Long getActiveLeafId(String conversationId) {
        List<ActiveMessageNode> branch = getActiveBranch(conversationId);
        return branch.isEmpty() ?
                null :
                branch.getLast().message().getId();
    }

    @Transactional(readOnly = true)
    public List<Long> getSiblingIds(String conversationId, @Nullable Long parentId) {
        return chatMessageRepository
                .findByConversation_IdOrderByIdAsc(conversationId)
                .stream()
                .filter(m -> Objects.equals(m.getParentId(), parentId))
                .map(ChatMessage::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getAncestors(String conversationId, Long leafMessageId, int maxMessages) {
        if (conversationId == null || conversationId.isBlank() || leafMessageId == null || maxMessages <= 0) {
            return List.of();
        }
        List<ChatMessage> all = chatMessageRepository.findByConversation_IdOrderByIdAsc(conversationId);
        if (all.isEmpty()) {
            return List.of();
        }
        Map<Long, ChatMessage> byId = new HashMap<>(all.size() * 2);
        for (ChatMessage msg : all) {
            byId.put(msg.getId(), msg);
        }
        List<ChatMessage> reversed = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Long cursorId = leafMessageId;
        while (cursorId != null && reversed.size() < maxMessages && visited.add(cursorId)) {
            ChatMessage msg = byId.get(cursorId);
            if (msg == null) {
                break;
            }
            reversed.add(msg);
            cursorId = msg.getParentId();
        }
        Collections.reverse(reversed);
        return reversed;
    }

    @Transactional
    public Optional<ChatMessage> selectMessageVersion(String conversationId, Long messageId) {
        Optional<ChatMessage> targetOpt = chatMessageRepository.findByIdAndConversation_Id(messageId, conversationId);
        if (targetOpt.isEmpty()) {
            return Optional.empty();
        }
        Long parentId = targetOpt.get().getParentId();
        deactivateSiblings(conversationId, parentId);
        ChatMessage refreshed = chatMessageRepository
                .findByIdAndConversation_Id(messageId, conversationId)
                .orElseThrow();
        refreshed.setActive(true);
        return Optional.of(chatMessageRepository.save(refreshed));
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getConversationMessages(String conversationId) {
        return getActiveBranch(conversationId).stream().map(ActiveMessageNode::message).toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getConversationMessages(String conversationId, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 1000));
        List<ChatMessage> active = getConversationMessages(conversationId);
        if (active.size() <= boundedLimit) {
            return active;
        }
        return active.subList(active.size() - boundedLimit, active.size());
    }

    @Transactional
    public void deleteByConversationId(String conversationId) {
        chatMessageRepository.deleteAllByConversationId(conversationId);
    }

    private void deactivateSiblings(String conversationId, @Nullable Long parentId) {
        if (parentId == null) {
            chatMessageRepository.deactivateRootSiblings(conversationId);
        } else {
            chatMessageRepository.deactivateChildSiblings(conversationId, parentId);
        }
    }

    public List<String> deserializeSelectedSourceIds(ChatMessage message) {
        return fromJson(message.getSelectedSourceIds(), STRING_LIST, List.of());
    }

    public List<UUID> deserializeSelectedSourceUuids(ChatMessage message) {
        return deserializeSelectedSourceIds(message).stream().map(id -> {
            try {
                return UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }).filter(Objects::nonNull).toList();
    }

    public List<ChatReplyMessage.CitedSource> deserializeCitedSources(ChatMessage message) {
        return fromJson(message.getCitedSources(), CITED_SOURCE_LIST, List.of());
    }

    public List<AgentStep> deserializeThinkingSteps(ChatMessage message) {
        return fromJson(message.getThinkingSteps(), AGENT_STEP_LIST, List.of());
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
