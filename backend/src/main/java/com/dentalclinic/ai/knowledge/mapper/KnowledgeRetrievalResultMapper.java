package com.dentalclinic.ai.knowledge.mapper;

import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalCandidate;
import com.dentalclinic.ai.knowledge.model.KnowledgeRetrievalResult;
import com.dentalclinic.ai.knowledge.model.KnowledgeSourceTrace;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class KnowledgeRetrievalResultMapper {

    private final ObjectMapper objectMapper;

    public KnowledgeRetrievalResultMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public KnowledgeRetrievalResult toResult(KnowledgeRetrievalCandidate candidate) {
        JsonNode metadata = parseMetadata(candidate.metadataJson());
        KnowledgeSourceTrace source = new KnowledgeSourceTrace(
                requiredText(metadata, "sourceKey"),
                requiredText(metadata, "sourceVersion"),
                requiredText(metadata, "recordId"),
                requiredText(metadata, "title"),
                requiredText(metadata, "sourceUrl")
        );
        return new KnowledgeRetrievalResult(
                candidate.documentId(),
                candidate.content(),
                requiredText(metadata, "category"),
                requiredText(metadata, "language"),
                textList(metadata, "symptoms"),
                textList(metadata, "serviceCategoryHints"),
                textList(metadata, "specializationHints"),
                source,
                candidate.score()
        );
    }

    private JsonNode parseMetadata(String metadataJson) {
        try {
            JsonNode metadata = objectMapper.readTree(metadataJson);
            if (metadata == null || !metadata.isObject()) {
                throw invalidMetadata();
            }
            return metadata;
        } catch (JsonProcessingException exception) {
            throw invalidMetadata();
        }
    }

    private String requiredText(JsonNode metadata, String fieldName) {
        JsonNode value = metadata.path(fieldName);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw invalidMetadata();
        }
        return value.asText();
    }

    private List<String> textList(JsonNode metadata, String fieldName) {
        JsonNode values = metadata.path(fieldName);
        if (!values.isArray()) {
            throw invalidMetadata();
        }
        List<String> result = new ArrayList<>(values.size());
        values.forEach(value -> {
            if (!value.isTextual() || value.asText().isBlank()) {
                throw invalidMetadata();
            }
            result.add(value.asText());
        });
        return List.copyOf(result);
    }

    private KnowledgeBaseException invalidMetadata() {
        return new KnowledgeBaseException("Metadata vector kiến thức không hợp lệ");
    }
}
