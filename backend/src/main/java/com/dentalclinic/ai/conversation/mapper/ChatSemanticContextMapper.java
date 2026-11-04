package com.dentalclinic.ai.conversation.mapper;

import com.dentalclinic.ai.conversation.model.ChatDateRange;
import com.dentalclinic.ai.conversation.model.ChatEntityReference;
import com.dentalclinic.ai.conversation.model.ChatIntent;
import com.dentalclinic.ai.conversation.model.ChatSemanticContext;
import com.dentalclinic.ai.conversation.model.ChatSessionPreference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ChatSemanticContextMapper {

    public ObjectNode toJson(ChatSemanticContext context) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ArrayNode intents = root.putArray("lastIntents");
        context.lastIntents().forEach(intent -> intents.add(intent.name()));
        ArrayNode symptoms = root.putArray("symptoms");
        context.symptoms().forEach(symptoms::add);
        putReference(root, "selectedDoctor", context.selectedDoctor());
        putReference(root, "selectedService", context.selectedService());
        if (context.dateRange() != null) {
            ObjectNode dateRange = root.putObject("dateRange");
            dateRange.put("from", context.dateRange().from().toString());
            dateRange.put("to", context.dateRange().to().toString());
        }
        if (context.sessionPreference() != null) {
            root.put("sessionPreference", context.sessionPreference().name());
        }
        if (context.timePreference() != null) {
            root.put("timePreference", context.timePreference());
        }
        return root;
    }

    public ChatSemanticContext fromJson(JsonNode value) {
        if (value == null || value.isNull() || (value.isObject() && value.isEmpty())) {
            return ChatSemanticContext.empty();
        }
        if (!value.isObject()) {
            throw invalidContext();
        }
        try {
            return new ChatSemanticContext(
                    parseIntents(value.path("lastIntents")),
                    parseTextArray(value.path("symptoms")),
                    parseReference(value.path("selectedDoctor")),
                    parseReference(value.path("selectedService")),
                    parseDateRange(value.path("dateRange")),
                    parseSessionPreference(value.path("sessionPreference")),
                    optionalText(value.path("timePreference"))
            );
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalidContext();
        }
    }

    private void putReference(ObjectNode root, String fieldName, ChatEntityReference reference) {
        if (reference == null) {
            return;
        }
        ObjectNode node = root.putObject(fieldName);
        if (reference.id() != null) {
            node.put("id", reference.id().toString());
        }
        if (reference.name() != null) {
            node.put("name", reference.name());
        }
    }

    private List<ChatIntent> parseIntents(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw invalidContext();
        }
        List<ChatIntent> result = new ArrayList<>(node.size());
        node.forEach(value -> result.add(ChatIntent.valueOf(requiredText(value))));
        return List.copyOf(result);
    }

    private List<String> parseTextArray(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw invalidContext();
        }
        List<String> result = new ArrayList<>(node.size());
        node.forEach(value -> result.add(requiredText(value)));
        return List.copyOf(result);
    }

    private ChatEntityReference parseReference(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw invalidContext();
        }
        String idValue = optionalText(node.path("id"));
        UUID id = idValue == null ? null : UUID.fromString(idValue);
        return new ChatEntityReference(id, optionalText(node.path("name")));
    }

    private ChatDateRange parseDateRange(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw invalidContext();
        }
        return new ChatDateRange(
                LocalDate.parse(requiredText(node.path("from"))),
                LocalDate.parse(requiredText(node.path("to")))
        );
    }

    private ChatSessionPreference parseSessionPreference(JsonNode node) {
        String value = optionalText(node);
        return value == null ? null : ChatSessionPreference.valueOf(value);
    }

    private String requiredText(JsonNode node) {
        String value = optionalText(node);
        if (value == null) {
            throw invalidContext();
        }
        return value;
    }

    private String optionalText(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isTextual() || node.asText().isBlank()) {
            throw invalidContext();
        }
        return node.asText().strip();
    }

    private IllegalArgumentException invalidContext() {
        return new IllegalArgumentException("Chat semantic context is invalid");
    }
}
