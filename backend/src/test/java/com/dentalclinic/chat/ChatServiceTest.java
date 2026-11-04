package com.dentalclinic.chat;

import com.dentalclinic.chat.dto.SendChatMessageRequest;
import com.dentalclinic.chat.dto.SendChatMessageResponse;
import com.dentalclinic.chat.service.ChatOrchestrator;
import com.dentalclinic.chat.service.ChatPersistenceService;
import com.dentalclinic.chat.service.ChatService;
import com.dentalclinic.chat.service.ChatStreamingService;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private final ChatPersistenceService persistenceService = mock(ChatPersistenceService.class);
    private final ChatOrchestrator orchestrator = mock(ChatOrchestrator.class);
    private final ChatStreamingService streamingService = mock(ChatStreamingService.class);
    private final ChatService service = new ChatService(
            persistenceService,
            orchestrator,
            streamingService
    );

    @Test
    void shouldDelegateMessageToOrchestrator() {
        UUID userId = UUID.randomUUID();
        SendChatMessageRequest request = new SendChatMessageRequest("Tôi bị đau răng");
        SendChatMessageResponse expected = new SendChatMessageResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Bạn nên đến nha sĩ để được kiểm tra.",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                Instant.parse("2026-10-29T03:00:00Z")
        );
        when(orchestrator.sendMessage(userId, request)).thenReturn(expected);

        assertThat(service.sendMessage(userId, request)).isSameAs(expected);
        verify(orchestrator).sendMessage(userId, request);
    }

    @Test
    void shouldDelegateStreamingMessage() {
        UUID userId = UUID.randomUUID();
        SendChatMessageRequest request = new SendChatMessageRequest("Tìm lịch ngày mai");
        Flux<ServerSentEvent<Object>> expected = Flux.just(
                ServerSentEvent.builder((Object) "started").event("started").build()
        );
        when(streamingService.streamMessage(userId, request)).thenReturn(expected);

        assertThat(service.streamMessage(userId, request)).isSameAs(expected);
        verify(streamingService).streamMessage(userId, request);
    }
}
