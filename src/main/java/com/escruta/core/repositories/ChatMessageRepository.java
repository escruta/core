package com.escruta.core.repositories;

import com.escruta.core.entities.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends CrudRepository<ChatMessage, Long> {
    List<ChatMessage> findByConversation_IdOrderByIdAsc(String conversationId);

    List<ChatMessage> findByConversation_IdOrderByIdDesc(String conversationId, Pageable pageable);

    void deleteByConversation_Id(String conversationId);
}
