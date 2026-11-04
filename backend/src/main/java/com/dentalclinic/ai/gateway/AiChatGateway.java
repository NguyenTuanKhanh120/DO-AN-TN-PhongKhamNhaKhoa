package com.dentalclinic.ai.gateway;

public interface AiChatGateway {

    String generate(String systemPrompt, String userPrompt);
}
