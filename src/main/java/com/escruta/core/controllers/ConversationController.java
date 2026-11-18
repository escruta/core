package com.escruta.core.controllers;

import com.escruta.core.dtos.conversation.ConversationMessagesResponse;
import com.escruta.core.dtos.conversation.ConversationResponse;
import com.escruta.core.dtos.conversation.ConversationUpdateDTO;
import com.escruta.core.dtos.conversation.ConversationsPageResponse;
import com.escruta.core.entities.ChatMessage;
import com.escruta.core.entities.Conversation;
import com.escruta.core.repositories.ConversationRepository;
import com.escruta.core.repositories.OffsetPageRequest;
import com.escruta.core.services.ChatMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("notebooks/{notebookId}/conversations")
@RequiredArgsConstructor
class ConversationController {
    private final ConversationRepository conversationRepository;
    private final ChatMessageService chatMessageService;

    @GetMapping
    ResponseEntity<ConversationsPageResponse> getConversations(
            @PathVariable UUID notebookId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "Newest") String sort
    ) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        long boundedOffset = Math.max(0, offset);
        var pageable = new OffsetPageRequest(boundedOffset, boundedLimit, buildSort(sort));

        List<Conversation> conversations;
        long total;

        if (search != null && !search.isBlank()) {
            String term = search.trim();
            conversations = conversationRepository.findByNotebookIdAndTitleContainingIgnoreCase(
                    notebookId,
                    term,
                    pageable
            );
            total = conversationRepository.countByNotebookIdAndTitleContainingIgnoreCase(notebookId, term);
        } else {
            conversations = conversationRepository.findByNotebookId(notebookId, pageable);
            total = conversationRepository.countByNotebookId(notebookId);
        }

        var response = conversations.stream().map(c -> new ConversationResponse(
                c.getId(),
                c.getTitle(),
                c.getMode(),
                c.getStatus(),
                c.isHasUnseenReply(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        )).toList();

        boolean hasMore = (boundedOffset + conversations.size()) < total;

        return ResponseEntity.ok(new ConversationsPageResponse(response, total, hasMore));
    }

    @GetMapping("/{conversationId}")
    ResponseEntity<ConversationMessagesResponse> getConversationMessages(
            @PathVariable UUID notebookId,
            @PathVariable String conversationId
    ) {
        var conversation = conversationRepository.findByIdAndNotebookId(conversationId, notebookId).orElse(null);

        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(buildConversationMessagesResponse(conversation));
    }

    @PatchMapping("/{conversationId}/messages/{messageId}/select")
    ResponseEntity<ConversationMessagesResponse> selectMessageVersion(
            @PathVariable UUID notebookId,
            @PathVariable String conversationId,
            @PathVariable Long messageId
    ) {
        var conversation = conversationRepository.findByIdAndNotebookId(conversationId, notebookId).orElse(null);

        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }

        var selected = chatMessageService.selectMessageVersion(conversationId, messageId);
        if (selected.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(buildConversationMessagesResponse(conversation));
    }

    private ConversationMessagesResponse buildConversationMessagesResponse(Conversation conversation) {
        String conversationId = conversation.getId();
        List<ChatMessageService.ActiveMessageNode> activeBranch = chatMessageService.getActiveBranch(conversationId);

        var messageResponses = activeBranch.stream().map(node -> {
            ChatMessage m = node.message();
            return new ConversationMessagesResponse.MessageResponse(
                    m.getId(),
                    m.getParentId(),
                    node.siblingIds(),
                    m.getContent(),
                    m.getRole(),
                    chatMessageService.deserializeCitedSources(m),
                    chatMessageService.deserializeSelectedSourceIds(m).size(),
                    chatMessageService.deserializeThinkingSteps(m),
                    m.getThinkingDurationMs()
            );
        }).toList();

        return new ConversationMessagesResponse(
                conversationId,
                conversation.getMode(),
                conversation.getStatus(),
                conversation.isHasUnseenReply(),
                messageResponses
        );
    }

    @PatchMapping("/{conversationId}")
    ResponseEntity<ConversationResponse> renameConversation(
            @PathVariable UUID notebookId,
            @PathVariable String conversationId,
            @Valid @RequestBody
            ConversationUpdateDTO conversationUpdateDTO
    ) {
        var conversation = conversationRepository.findByIdAndNotebookId(conversationId, notebookId).orElse(null);

        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }

        conversation.setTitle(conversationUpdateDTO.title().trim());
        conversationRepository.save(conversation);

        return ResponseEntity.ok(new ConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getMode(),
                conversation.getStatus(),
                conversation.isHasUnseenReply(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        ));
    }

    @PatchMapping("/{conversationId}/seen")
    ResponseEntity<Void> markConversationAsSeen(@PathVariable UUID notebookId, @PathVariable String conversationId) {
        var conversation = conversationRepository.findByIdAndNotebookId(conversationId, notebookId).orElse(null);

        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }

        if (conversation.isHasUnseenReply()) {
            conversation.setHasUnseenReply(false);
            conversationRepository.save(conversation);
        }

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{conversationId}")
    @Transactional
    ResponseEntity<Void> deleteConversation(@PathVariable UUID notebookId, @PathVariable String conversationId) {
        var conversation = conversationRepository.findByIdAndNotebookId(conversationId, notebookId).orElse(null);

        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }

        chatMessageService.deleteByConversationId(conversationId);
        conversationRepository.delete(conversation);

        return ResponseEntity.noContent().build();
    }

    private Sort buildSort(String sort) {
        return switch (sort) {
            case "Oldest" -> Sort.by(Sort.Direction.ASC, "updatedAt");
            case "Alphabetical" -> Sort.by(Sort.Direction.ASC, "title");
            case "Reverse Alphabetical" -> Sort.by(Sort.Direction.DESC, "title");
            default -> Sort.by(Sort.Direction.DESC, "updatedAt");
        };
    }
}
