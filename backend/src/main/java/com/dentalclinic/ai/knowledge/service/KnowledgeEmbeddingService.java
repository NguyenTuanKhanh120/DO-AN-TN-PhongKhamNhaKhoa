package com.dentalclinic.ai.knowledge.service;

import com.dentalclinic.ai.embedding.EmbeddingGateway;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.model.DentalKnowledgeDataset;
import com.dentalclinic.ai.knowledge.model.EmbeddedKnowledgeChunk;
import com.dentalclinic.ai.knowledge.model.KnowledgeChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class KnowledgeEmbeddingService {

    private final KnowledgeChunker chunker;
    private final Optional<EmbeddingGateway> embeddingGateway;

    public KnowledgeEmbeddingService(
            KnowledgeChunker chunker,
            Optional<EmbeddingGateway> embeddingGateway
    ) {
        this.chunker = chunker;
        this.embeddingGateway = embeddingGateway;
    }

    public List<EmbeddedKnowledgeChunk> embed(DentalKnowledgeDataset dataset) {
        List<KnowledgeChunk> chunks = chunker.chunk(dataset);
        return embedChunks(chunks);
    }

    public List<KnowledgeChunk> chunk(DentalKnowledgeDataset dataset) {
        return chunker.chunk(dataset);
    }

    public List<EmbeddedKnowledgeChunk> embedChunks(List<KnowledgeChunk> chunks) {
        EmbeddingGateway gateway = requireGateway();
        if (chunks.isEmpty()) {
            return List.of();
        }

        List<float[]> embeddings = gateway.embedAll(
                chunks.stream().map(KnowledgeChunk::content).toList()
        );
        if (embeddings == null || embeddings.size() != chunks.size()) {
            throw new KnowledgeBaseException("Số embedding không khớp số chunk kiến thức");
        }

        List<EmbeddedKnowledgeChunk> result = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            float[] embedding = requireValidEmbedding(
                    embeddings.get(index),
                    index,
                    gateway.dimension()
            );
            result.add(new EmbeddedKnowledgeChunk(
                    chunks.get(index),
                    gateway.modelName(),
                    gateway.dimension(),
                    embedding
            ));
        }
        return List.copyOf(result);
    }

    public String embeddingModel() {
        return requireGateway().modelName();
    }

    public int embeddingDimension() {
        return requireGateway().dimension();
    }

    private EmbeddingGateway requireGateway() {
        return embeddingGateway.orElseThrow(() -> new KnowledgeBaseException(
                "Embedding chưa được bật trên hệ thống"
        ));
    }

    private float[] requireValidEmbedding(float[] embedding, int index, int expectedDimension) {
        if (embedding == null || embedding.length != expectedDimension) {
            throw new KnowledgeBaseException(
                    "Embedding thứ " + index + " không đúng " + expectedDimension + " chiều"
            );
        }
        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new KnowledgeBaseException("Embedding thứ " + index + " chứa giá trị không hữu hạn");
            }
        }
        return embedding.clone();
    }
}
