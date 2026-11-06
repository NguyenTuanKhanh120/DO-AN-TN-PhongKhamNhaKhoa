package com.dentalclinic.ai.embedding;

import com.dentalclinic.ai.config.AiProperties;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.google.genai.Models;
import com.google.genai.errors.ApiException;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GoogleGenAiEmbeddingGateway implements EmbeddingGateway {

    private static final int MAX_TEXTS_PER_REQUEST = 30;
    private static final long MINIMUM_BATCH_INTERVAL_MILLIS = 21_000;

    private final Models models;
    private final String modelName;
    private final int dimension;
    private final EmbedContentConfig embeddingConfig;
    private final Sleeper sleeper;

    public GoogleGenAiEmbeddingGateway(Models models, AiProperties properties) {
        this(models, properties, Thread::sleep);
    }

    GoogleGenAiEmbeddingGateway(
            Models models,
            AiProperties properties,
            Sleeper sleeper
    ) {
        this.models = models;
        this.modelName = properties.embeddingModel().trim();
        this.dimension = properties.embeddingDimension();
        this.embeddingConfig = EmbedContentConfig.builder()
                .outputDimensionality(dimension)
                .build();
        this.sleeper = sleeper;
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        List<String> normalizedTexts = validateTexts(texts);
        try {
            List<float[]> result = new ArrayList<>(normalizedTexts.size());
            for (int start = 0; start < normalizedTexts.size(); start += MAX_TEXTS_PER_REQUEST) {
                if (start > 0) {
                    pauseBetweenBatches();
                }
                int end = Math.min(start + MAX_TEXTS_PER_REQUEST, normalizedTexts.size());
                List<String> batch = normalizedTexts.subList(start, end);
                result.addAll(embedBatch(batch, start));
            }
            return List.copyOf(result);
        } catch (KnowledgeBaseException exception) {
            throw exception;
        } catch (ApiException exception) {
            throw unavailable(providerFailureMessage(exception));
        } catch (RuntimeException exception) {
            throw unavailable("Không thể tạo embedding từ Gemini");
        }
    }

    private void pauseBetweenBatches() {
        try {
            sleeper.sleep(MINIMUM_BATCH_INTERVAL_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("Tiến trình tạo embedding đã bị gián đoạn");
        }
    }

    private List<float[]> embedBatch(List<String> texts, int offset) {
        EmbedContentResponse response = models.embedContent(
                modelName,
                texts,
                embeddingConfig
        );
        List<ContentEmbedding> embeddings = response.embeddings()
                .orElseThrow(() -> unavailable("Gemini không trả về embedding"));
        if (embeddings.size() != texts.size()) {
            throw unavailable("Số embedding không khớp số nội dung đầu vào");
        }

        List<float[]> result = new ArrayList<>(embeddings.size());
        for (int index = 0; index < embeddings.size(); index++) {
            int embeddingIndex = offset + index;
            List<Float> values = embeddings.get(index).values()
                    .orElseThrow(() -> unavailable(
                            "Embedding thứ " + embeddingIndex + " không có dữ liệu"
                    ));
            result.add(validateAndNormalize(values, embeddingIndex));
        }
        return result;
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

    private String providerFailureMessage(ApiException exception) {
        if (exception.code() == 429) {
            String providerMessage = exception.message() == null
                    ? ""
                    : exception.message().toLowerCase(Locale.ROOT);
            return providerMessage.contains("per day")
                    ? "Gemini embedding đã hết hạn mức theo ngày"
                    : "Gemini embedding đang vượt giới hạn tần suất; hãy thử lại sau";
        }
        if (exception.code() == 401 || exception.code() == 403) {
            return "Gemini từ chối thông tin xác thực embedding";
        }
        if (exception.code() == 400) {
            return "Gemini từ chối nội dung embedding không hợp lệ";
        }
        return "Không thể tạo embedding từ Gemini";
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
