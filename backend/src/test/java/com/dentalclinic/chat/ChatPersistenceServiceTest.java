package com.dentalclinic.chat;

import com.dentalclinic.chat.dto.ChatMessageResponse;
import com.dentalclinic.chat.entity.ChatMessage;
import com.dentalclinic.chat.entity.ChatSender;
import com.dentalclinic.chat.entity.ChatSession;
import com.dentalclinic.chat.mapper.ChatMapper;
import com.dentalclinic.chat.repository.ChatMessageRepository;
import com.dentalclinic.chat.repository.ChatSessionRepository;
import com.dentalclinic.chat.service.ChatPersistenceService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatPersistenceServiceTest {

    private final ChatSessionRepository sessionRepository = mock(ChatSessionRepository.class);
    private final ChatMessageRepository messageRepository = mock(ChatMessageRepository.class);
    private final ChatPersistenceService service = new ChatPersistenceService(
            sessionRepository,
            messageRepository,
            new ChatMapper()
    );

    @Test
    void shouldCreateSessionIdempotentlyAndReturnIt() {
        UUID userId = UUID.randomUUID();
        ChatSession session = session(userId);
        when(sessionRepository.findByUserId(userId)).thenReturn(Optional.of(session));

        var response = service.getSession(userId);

        verify(sessionRepository).insertIfAbsent(userId);
        assertThat(response.id()).isEqualTo(session.getId());
        assertThat(response.title()).isNull();
    }

    @Test
    void shouldReturnOnlyMessagesFromOwnedSession() {
        UUID userId = UUID.randomUUID();
        ChatSession session = session(userId);
        ChatMessage message = message(session, ChatSender.USER, "Xin chào");
        when(sessionRepository.findByUserId(userId)).thenReturn(Optional.of(session));
        when(messageRepository.findAllBySessionId(eq(session.getId()), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(message),
                        invocation.getArgument(1),
                        1
                ));

        var response = service.getMessages(userId, 0, 20);

        assertThat(response.content())
                .extracting(ChatMessageResponse::content)
                .containsExactly("Xin chào");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void shouldDeleteMessagesAndResetContextWhileKeepingSession() {
        UUID userId = UUID.randomUUID();
        ChatSession session = session(userId);
        session.setContextData(JsonNodeFactory.instance.objectNode().put("symptom", "đau răng"));
        when(sessionRepository.findByUserId(userId)).thenReturn(Optional.of(session));
        when(sessionRepository.saveAndFlush(session)).thenReturn(session);

        service.clearHistory(userId);

        verify(messageRepository).deleteAllBySessionId(session.getId());
        verify(sessionRepository).saveAndFlush(session);
        assertThat(session.getContextData().isObject()).isTrue();
        assertThat(session.getContextData().isEmpty()).isTrue();
    }

    @Test
    void shouldAppendMessageWithEmptyMetadataObject() {
        UUID userId = UUID.randomUUID();
        ChatSession session = session(userId);
        when(sessionRepository.findByUserId(userId)).thenReturn(Optional.of(session));
        when(messageRepository.saveAndFlush(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(UUID.randomUUID());
            message.setCreatedAt(Instant.parse("2026-10-29T03:00:00Z"));
            return message;
        });

        ChatMessageResponse response = service.appendMessage(userId, ChatSender.USER, "Xin chào");

        assertThat(response.sender()).isEqualTo(ChatSender.USER);
        assertThat(response.content()).isEqualTo("Xin chào");
        assertThat(response.metadata().isObject()).isTrue();
        assertThat(response.metadata().isEmpty()).isTrue();
    }

    @Test
    void shouldPersistTurnInShortStepsAndStoreAssistantMetadataAndContext() {
        UUID userId = UUID.randomUUID();
        ChatSession session = session(userId);
        when(sessionRepository.findByUserId(userId)).thenReturn(Optional.of(session));
        when(sessionRepository.saveAndFlush(session)).thenReturn(session);
        when(messageRepository.saveAndFlush(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(UUID.randomUUID());
            message.setCreatedAt(Instant.parse("2026-10-29T03:00:00Z"));
            return message;
        });

        var start = service.startTurn(userId, "Tôi bị đau răng");
        var metadata = JsonNodeFactory.instance.objectNode().put("needsClarification", false);
        var context = JsonNodeFactory.instance.objectNode();
        context.putArray("symptoms").add("đau răng");
        var assistant = service.completeTurn(
                userId,
                "Bạn nên đi khám trực tiếp.",
                metadata,
                context
        );

        assertThat(start.userMessage().sender()).isEqualTo(ChatSender.USER);
        assertThat(start.contextData().isEmpty()).isTrue();
        assertThat(assistant.sender()).isEqualTo(ChatSender.ASSISTANT);
        assertThat(assistant.metadata().path("needsClarification").asBoolean()).isFalse();
        assertThat(session.getContextData().path("symptoms").get(0).asText())
                .isEqualTo("đau răng");
        verify(sessionRepository).saveAndFlush(session);
    }

    private ChatSession session(UUID userId) {
        ChatSession session = new ChatSession();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setCreatedAt(Instant.parse("2026-10-29T02:00:00Z"));
        session.setUpdatedAt(Instant.parse("2026-10-29T02:00:00Z"));
        return session;
    }

    private ChatMessage message(ChatSession session, ChatSender sender, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID());
        message.setSession(session);
        message.setSender(sender);
        message.setContent(content);
        message.setCreatedAt(Instant.parse("2026-10-29T03:00:00Z"));
        return message;
    }
}
