package com.dentalclinic.ai.embedding;

import com.dentalclinic.ai.config.AiProperties;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.google.genai.Models;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;

import java.util.ArrayList;
import java.util.List;

public class GoogleGenAiEmbeddingGateway implements EmbeddingGateway {

    private final Models models;
    private final String modelName;
    private final int dimension;
    private final EmbedContentConfig embeddingConfig;

    public GoogleGenAiEmbeddingGateway(Models models, AiProperties properties) {
        this.models = models;
        this.modelName = properties.embeddingModel().trim();
        this.dimension = properties.embeddingDimension();
        this.embeddingConfig = EmbedContentConfig.builder()
                .outputDimensionality(dimension)
                .build();
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        List<String> normalizedTexts = validateTexts(texts);
        try {
            EmbedContentResponse response = models.embedContent(
                    modelName,
                    normalizedTexts,
                    embeddingConfig
            );
            List<ContentEmbedding> embeddings = response.embeddings()
                    .orElseThrow(() -> unavailable("Gemini không trả về embedding"));
            if (embeddings.size() != normalizedTexts.size()) {
                throw unavailable("Số embedding không khớp số nội dung đầu vào");
            }

            List<float[]> result = new ArrayList<>(embeddings.size());
            for (int index = 0; index < embeddings.size(); index++) {
                int embeddingIndex = index;
                List<Float> values = embeddings.get(index).values()
                        .orElseThrow(() -> unavailable(
                                "Embedding thứ " + embeddingIndex + " không có dữ liệu"
                        ));
                result.add(validateAndNormalize(values, index));
            }
            return List.copyOf(result);
        } catch (KnowledgeBaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable("Không thể tạo embedding từ Gemini");
        }
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    private List<String> validateTexts(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("Embedding input must not be empty");
        }
        List<String> normalized = new ArrayList<>(texts.size());
        for (int index = 0; index < texts.size(); index++) {
            String text = texts.get(index);
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Embedding input at index " + index + " must not be blank");
            }
            normalized.add(text.strip());
        }
        return List.copyOf(normalized);
    }

    private float[] validateAndNormalize(List<Float> values, int embeddingIndex) {
        if (values.size() != dimension) {
            throw unavailable(
                    "Embedding thứ " + embeddingIndex + " phải có đúng " + dimension + " chiều"
            );
        }

        float[] vector = new float[dimension];
        double squaredNorm = 0.0;
        for (int index = 0; index < values.size(); index++) {
            Float value = values.get(index);
            if (value == null || !Float.isFinite(value)) {
                throw unavailable("Embedding chứa giá trị không hữu hạn tại vị trí " + index);
            }
            vector[index] = value;
            squaredNorm += (double) value * value;
        }

        double norm = Math.sqrt(squaredNorm);
        if (!Double.isFinite(norm) || norm == 0.0) {
            throw unavailable("Embedding có chuẩn vector không hợp lệ");
        }
        for (int index = 0; index < vector.length; index++) {
            vector[index] = (float) (vector[index] / norm);
        }
        return vector;
    }

    private KnowledgeBaseException unavailable(String message) {
        return new KnowledgeBaseException(message);
    }
}
