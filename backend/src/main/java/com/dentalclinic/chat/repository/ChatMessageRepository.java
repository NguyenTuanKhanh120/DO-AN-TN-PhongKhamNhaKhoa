package com.dentalclinic.chat.repository;

import com.dentalclinic.chat.entity.ChatMessage;
import com.dentalclinic.chat.entity.ChatSender;
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

    @Modifying
    @Query("""
            delete from ChatMessage message
            where message.id = :messageId
              and message.session.id = :sessionId
              and message.sender = :sender
            """)
    int deleteByIdAndSessionIdAndSender(
            @Param("messageId") UUID messageId,
            @Param("sessionId") UUID sessionId,
            @Param("sender") ChatSender sender
    );
}
