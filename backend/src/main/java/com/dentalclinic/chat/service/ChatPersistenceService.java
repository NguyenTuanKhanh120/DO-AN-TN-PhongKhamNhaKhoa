package com.dentalclinic.chat.service;

import com.dentalclinic.chat.dto.ChatMessageResponse;
import com.dentalclinic.chat.dto.ChatSessionResponse;
import com.dentalclinic.chat.entity.ChatMessage;
import com.dentalclinic.chat.entity.ChatSender;
import com.dentalclinic.chat.entity.ChatSession;
import com.dentalclinic.chat.mapper.ChatMapper;
import com.dentalclinic.chat.model.ChatTurnStart;
import com.dentalclinic.chat.repository.ChatMessageRepository;
import com.dentalclinic.chat.repository.ChatSessionRepository;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.response.PageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ChatPersistenceService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMapper chatMapper;

    public ChatPersistenceService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            ChatMapper chatMapper
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatMapper = chatMapper;
    }

    @Transactional
    public ChatSessionResponse getSession(UUID userId) {
        return chatMapper.toSessionResponse(getOrCreateSession(userId));
    }

    @Transactional
    public PageResponse<ChatMessageResponse> getMessages(UUID userId, int page, int size) {
        ChatSession session = getOrCreateSession(userId);
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<ChatMessageResponse> messages = chatMessageRepository
                .findAllBySessionId(session.getId(), pageRequest)
                .map(chatMapper::toMessageResponse);
        return PageResponse.from(messages);
    }

    @Transactional
    public ChatMessageResponse appendMessage(UUID userId, ChatSender sender, String content) {
        ChatSession session = getOrCreateSession(userId);
        return appendMessage(
                session,
                sender,
                content,
                JsonNodeFactory.instance.objectNode()
        );
    }

    @Transactional
    public ChatTurnStart startTurn(UUID userId, String content) {
        ChatSession session = getOrCreateSession(userId);
        ChatMessageResponse userMessage = appendMessage(
                session,
                ChatSender.USER,
                content,
                JsonNodeFactory.instance.objectNode()
        );
        JsonNode contextData = session.getContextData() == null
                ? JsonNodeFactory.instance.objectNode()
                : session.getContextData();
        return new ChatTurnStart(userMessage, contextData);
    }

    @Transactional
    public ChatMessageResponse completeTurn(
            UUID userId,
            String answer,
            JsonNode metadata,
            JsonNode contextData
    ) {
        ChatSession session = getOrCreateSession(userId);
        session.setContextData(requireObject(contextData, "contextData"));
        chatSessionRepository.saveAndFlush(session);
        return appendMessage(
                session,
                ChatSender.ASSISTANT,
                answer,
                requireObject(metadata, "metadata")
        );
    }

    private ChatMessageResponse appendMessage(
            ChatSession session,
            ChatSender sender,
            String content,
            JsonNode metadata
    ) {
        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setSender(sender);
        message.setContent(content);
        message.setMetadata(metadata.deepCopy());
        return chatMapper.toMessageResponse(chatMessageRepository.saveAndFlush(message));
    }

    @Transactional
    public void clearHistory(UUID userId) {
        ChatSession session = getOrCreateSession(userId);
        chatMessageRepository.deleteAllBySessionId(session.getId());
        session.setContextData(JsonNodeFactory.instance.objectNode());
        chatSessionRepository.saveAndFlush(session);
    }

    private ChatSession getOrCreateSession(UUID userId) {
        chatSessionRepository.insertIfAbsent(userId);
        return chatSessionRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Không thể khởi tạo phiên chatbot"));
    }

    private JsonNode requireObject(JsonNode value, String fieldName) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(fieldName + " must be a JSON object");
        }
        return value.deepCopy();
    }
}
