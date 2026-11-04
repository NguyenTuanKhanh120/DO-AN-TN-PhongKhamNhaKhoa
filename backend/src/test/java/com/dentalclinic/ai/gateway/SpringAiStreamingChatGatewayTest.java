package com.dentalclinic.ai.gateway;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiStreamingChatGatewayTest {

    private final GoogleGenAiChatModel chatModel = mock(GoogleGenAiChatModel.class);
    private final SpringAiStreamingChatGateway gateway =
            new SpringAiStreamingChatGateway(chatModel);

    @Test
    void shouldPreserveStreamChunksAndSendSeparatePromptMessages() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                response("Xin "),
                response("chào")
        ));

        List<String> chunks = gateway.stream("Chỉ dẫn an toàn", "Xin chào")
                .collectList()
                .block(Duration.ofSeconds(1));

        assertThat(chunks).containsExactly("Xin ", "chào");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getInstructions())
                .extracting(message -> message.getMessageType())
                .containsExactly(MessageType.SYSTEM, MessageType.USER);
        assertThat(promptCaptor.getValue().getSystemMessage().getText())
                .isEqualTo("Chỉ dẫn an toàn");
        assertThat(promptCaptor.getValue().getUserMessage().getText())
                .isEqualTo("Xin chào");
    }

    @Test
    void shouldIgnoreEmptyProviderChunks() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                response(""),
                response("Nội dung")
        ));

        assertThat(gateway.stream("Chỉ dẫn", "Câu hỏi")
                .collectList()
                .block(Duration.ofSeconds(1)))
                .containsExactly("Nội dung");
    }

    @Test
    void shouldRejectBlankPrompt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> gateway.stream(" ", "Câu hỏi"))
                .withMessage("System prompt must not be blank");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> gateway.stream("Chỉ dẫn", " "))
                .withMessage("User prompt must not be blank");
    }

    @Test
    void shouldFailStreamWhenProviderStopsAtOutputTokenLimit() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                response("Nội dung đầu"),
                response("Nội dung bị cắt", "MAX_TOKENS")
        ));

        assertThatThrownBy(() -> gateway.stream("Chỉ dẫn", "Câu hỏi")
                .collectList()
                .block(Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Chat model response exceeded the output token limit");
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private ChatResponse response(String content, String finishReason) {
        return new ChatResponse(List.of(new Generation(
                new AssistantMessage(content),
                ChatGenerationMetadata.builder().finishReason(finishReason).build()
        )));
    }
}
