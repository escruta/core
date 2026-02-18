package com.escruta.core.controllers;

import com.escruta.core.dtos.conversation.ConversationMessagesResponse;
import com.escruta.core.dtos.conversation.ConversationResponse;
import com.escruta.core.dtos.conversation.ConversationsPageResponse;
import com.escruta.core.entities.Conversation;
import com.escruta.core.repositories.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("notebooks/{notebookId}/conversations")
@RequiredArgsConstructor
class ConversationController {
    private final ConversationRepository conversationRepository;
    private final JdbcChatMemoryRepository chatMemoryRepository;

    @GetMapping
    ResponseEntity<ConversationsPageResponse> getConversations(
            @PathVariable UUID notebookId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) String search
    ) {
        var pageable = PageRequest.of(offset / limit, limit);

        List<Conversation> conversations;
        long total;

        if (search != null && !search.isBlank()) {
            conversations = conversationRepository.findByNotebookIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(notebookId,
                    search.trim(),
                    pageable
            );
            total = conversationRepository.countByNotebookIdAndTitleContainingIgnoreCase(notebookId, search.trim());
        } else {
            conversations = conversationRepository.findByNotebookIdOrderByUpdatedAtDesc(notebookId, pageable);
            total = conversationRepository.countByNotebookId(notebookId);
        }

        var response = conversations
                .stream()
                .map(c -> new ConversationResponse(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt()))
                .toList();

        boolean hasMore = (offset + conversations.size()) < total;

        return ResponseEntity.ok(new ConversationsPageResponse(response, total, hasMore));
    }

    @GetMapping("/{conversationId}")
    ResponseEntity<ConversationMessagesResponse> getConversationMessages(
            @PathVariable UUID notebookId,
            @PathVariable String conversationId
    ) {
        var conversation = conversationRepository.findById(conversationId).orElse(null);

        if (conversation == null || !conversation.getNotebook().getId().equals(notebookId)) {
            return ResponseEntity.notFound().build();
        }

        List<Message> messages = chatMemoryRepository.findByConversationId(conversationId);

        var messageResponses = messages
                .stream()
                .map(m -> new ConversationMessagesResponse.MessageResponse(m.getText(), m.getMessageType().name()))
                .toList();

        return ResponseEntity.ok(new ConversationMessagesResponse(conversationId, messageResponses));
    }

    @DeleteMapping("/{conversationId}")
    ResponseEntity<Void> deleteConversation(@PathVariable UUID notebookId, @PathVariable String conversationId) {
        var conversation = conversationRepository.findById(conversationId).orElse(null);

        if (conversation == null || !conversation.getNotebook().getId().equals(notebookId)) {
            return ResponseEntity.notFound().build();
        }

        chatMemoryRepository.deleteByConversationId(conversationId);
        conversationRepository.delete(conversation);

        return ResponseEntity.noContent().build();
    }
}
