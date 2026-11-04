package com.dentalclinic.chat.repository;

import com.dentalclinic.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    Optional<ChatSession> findByUserId(UUID userId);

    @Modifying
    @Query(value = """
            insert into chat_sessions (user_id)
            values (:userId)
            on conflict on constraint uq_chat_sessions_user do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId);
}
