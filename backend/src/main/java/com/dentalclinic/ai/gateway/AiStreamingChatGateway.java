package com.dentalclinic.ai.gateway;

import reactor.core.publisher.Flux;

public interface AiStreamingChatGateway {

    Flux<String> stream(String systemPrompt, String userPrompt);
}
