package com.dentalclinic.ai.gateway;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

public class SpringAiChatGateway implements AiChatGateway {

    private static final Set<String> TRUNCATED_FINISH_REASONS = Set.of("MAX_TOKENS", "LENGTH");

    private final ChatModel chatModel;

    public SpringAiChatGateway(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        Assert.hasText(systemPrompt, "System prompt must not be blank");
        Assert.hasText(userPrompt, "User prompt must not be blank");

        ChatResponse response = chatModel.call(new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userPrompt)
        )));
        if (response != null && response.hasFinishReasons(TRUNCATED_FINISH_REASONS)) {
            throw new IllegalStateException("Chat model response exceeded the output token limit");
        }
        String content = response == null || response.getResult() == null
                ? null
                : response.getResult().getOutput().getText();
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("Chat model returned an empty response");
        }
        return content.trim();
    }
}
