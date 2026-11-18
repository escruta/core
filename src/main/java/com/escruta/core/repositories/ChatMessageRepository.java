package com.escruta.core.repositories;

import com.escruta.core.entities.ChatMessage;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends CrudRepository<ChatMessage, Long> {
    List<ChatMessage> findByConversation_IdOrderByIdAsc(String conversationId);

    Optional<ChatMessage> findByIdAndConversation_Id(Long id, String conversationId);

    Optional<ChatMessage> findFirstByConversation_IdAndRoleAndIdLessThanOrderByIdDesc(
            String conversationId,
            String role,
            Long id
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ChatMessage m set m.active = false where m.conversation.id = :conversationId and m.parentId is null and m.active = true")
    void deactivateRootSiblings(@Param("conversationId") String conversationId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ChatMessage m set m.active = false where m.conversation.id = :conversationId and m.parentId = :parentId and m.active = true")
    void deactivateChildSiblings(@Param("conversationId") String conversationId, @Param("parentId") Long parentId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ChatMessage m where m.conversation.id = :conversationId")
    void deleteAllByConversationId(String conversationId);
}
