package com.dentalclinic.ai.gateway;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiChatGatewayTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final SpringAiChatGateway gateway = new SpringAiChatGateway(chatModel);

    @Test
    void shouldSendSeparateSystemAndUserMessagesToChatModel() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("  Chào bạn  "))
        )));

        String response = gateway.generate("Chỉ dẫn an toàn", "Xin chào");

        assertThat(response).isEqualTo("Chào bạn");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getInstructions())
                .extracting(message -> message.getMessageType())
                .containsExactly(MessageType.SYSTEM, MessageType.USER);
        assertThat(promptCaptor.getValue().getSystemMessage().getText())
                .isEqualTo("Chỉ dẫn an toàn");
        assertThat(promptCaptor.getValue().getUserMessage().getText())
                .isEqualTo("Xin chào");
    }

    @Test
    void shouldRejectBlankUserPrompt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> gateway.generate("Chỉ dẫn", "  "))
                .withMessage("User prompt must not be blank");
    }

    @Test
    void shouldRejectBlankSystemPrompt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> gateway.generate("  ", "Xin chào"))
                .withMessage("System prompt must not be blank");
    }

    @Test
    void shouldRejectResponseTruncatedByOutputTokenLimit() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(
                        new AssistantMessage("Nội dung chưa hoàn tất"),
                        ChatGenerationMetadata.builder().finishReason("MAX_TOKENS").build()
                )
        )));

        assertThatThrownBy(() -> gateway.generate("Chỉ dẫn", "Câu hỏi"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Chat model response exceeded the output token limit");
    }
}
