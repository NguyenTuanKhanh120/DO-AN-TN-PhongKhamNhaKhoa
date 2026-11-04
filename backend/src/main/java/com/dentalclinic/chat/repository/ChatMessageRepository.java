package com.dentalclinic.chat.repository;

import com.dentalclinic.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    Page<ChatMessage> findAllBySessionId(UUID sessionId, Pageable pageable);

    @Modifying
    @Query("delete from ChatMessage message where message.session.id = :sessionId")
    int deleteAllBySessionId(@Param("sessionId") UUID sessionId);
}
