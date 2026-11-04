package com.dentalclinic.chat.mapper;

import com.dentalclinic.chat.dto.ChatMessageResponse;
import com.dentalclinic.chat.dto.ChatSessionResponse;
import com.dentalclinic.chat.entity.ChatMessage;
import com.dentalclinic.chat.entity.ChatSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.stereotype.Component;

@Component
public class ChatMapper {

    public ChatSessionResponse toSessionResponse(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    public ChatMessageResponse toMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSender(),
                message.getContent(),
                copyObject(message.getMetadata()),
                message.getCreatedAt()
        );
    }

    private JsonNode copyObject(JsonNode value) {
        return value == null
                ? JsonNodeFactory.instance.objectNode()
                : value.deepCopy();
    }
}
