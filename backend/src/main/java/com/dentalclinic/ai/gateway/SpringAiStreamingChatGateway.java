package com.dentalclinic.ai.gateway;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;

public class SpringAiStreamingChatGateway implements AiStreamingChatGateway {

    private static final Set<String> TRUNCATED_FINISH_REASONS = Set.of("MAX_TOKENS", "LENGTH");

    private final GoogleGenAiChatModel chatModel;

    public SpringAiStreamingChatGateway(GoogleGenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Flux<String> stream(String systemPrompt, String userPrompt) {
        Assert.hasText(systemPrompt, "System prompt must not be blank");
        Assert.hasText(userPrompt, "User prompt must not be blank");

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userPrompt)
        ));
        return chatModel.stream(prompt).concatMap(response -> {
            if (response.hasFinishReasons(TRUNCATED_FINISH_REASONS)) {
                return Flux.error(new IllegalStateException(
                        "Chat model response exceeded the output token limit"
                ));
            }
            String content = response.getResult() == null
                    || response.getResult().getOutput() == null
                    ? null
                    : response.getResult().getOutput().getText();
            return content == null || content.isEmpty()
                    ? Flux.empty()
                    : Flux.just(content);
        });
    }
}
