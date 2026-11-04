package com.dentalclinic.ai.knowledge.service;

import com.dentalclinic.ai.knowledge.model.DentalKnowledgeDataset;
import com.dentalclinic.ai.knowledge.model.DentalKnowledgeRecord;
import com.dentalclinic.ai.knowledge.model.KnowledgeChunk;
import com.dentalclinic.ai.knowledge.model.KnowledgeChunkMetadata;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class KnowledgeChunker {

    public List<KnowledgeChunk> chunk(DentalKnowledgeDataset dataset) {
        Objects.requireNonNull(dataset, "dataset must not be null");
        return dataset.records().stream()
                .map(record -> toChunk(dataset, record))
                .toList();
    }

    private KnowledgeChunk toChunk(DentalKnowledgeDataset dataset, DentalKnowledgeRecord record) {
        KnowledgeChunkMetadata metadata = new KnowledgeChunkMetadata(
                dataset.datasetId(),
                dataset.version(),
                record.id(),
                record.title(),
                record.category(),
                dataset.language(),
                record.symptoms(),
                record.serviceCategoryHints(),
                record.specializationHints(),
                record.source().url().toString(),
                dataset.clinicalReviewStatus()
        );
        return new KnowledgeChunk(record.content(), metadata);
    }
}
