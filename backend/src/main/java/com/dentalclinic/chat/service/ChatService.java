package com.dentalclinic.chat.service;

import com.dentalclinic.chat.dto.ChatMessageResponse;
import com.dentalclinic.chat.dto.ChatSessionResponse;
import com.dentalclinic.chat.dto.SendChatMessageRequest;
import com.dentalclinic.chat.dto.SendChatMessageResponse;
import com.dentalclinic.common.response.PageResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Service
public class ChatService {

    private final ChatPersistenceService chatPersistenceService;
    private final ChatOrchestrator chatOrchestrator;
    private final ChatStreamingService chatStreamingService;

    public ChatService(
            ChatPersistenceService chatPersistenceService,
            ChatOrchestrator chatOrchestrator,
            ChatStreamingService chatStreamingService
    ) {
        this.chatPersistenceService = chatPersistenceService;
        this.chatOrchestrator = chatOrchestrator;
        this.chatStreamingService = chatStreamingService;
    }

    public ChatSessionResponse getSession(UUID userId) {
        return chatPersistenceService.getSession(userId);
    }

    public PageResponse<ChatMessageResponse> getMessages(UUID userId, int page, int size) {
        return chatPersistenceService.getMessages(userId, page, size);
    }

    public SendChatMessageResponse sendMessage(UUID userId, SendChatMessageRequest request) {
        return chatOrchestrator.sendMessage(userId, request);
    }

    public Flux<ServerSentEvent<Object>> streamMessage(
            UUID userId,
            SendChatMessageRequest request
    ) {
        return chatStreamingService.streamMessage(userId, request);
    }

    public void clearHistory(UUID userId) {
        chatPersistenceService.clearHistory(userId);
    }
}
