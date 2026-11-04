package com.dentalclinic.ai.knowledge.model;

import java.time.LocalDate;
import java.util.List;

public record DentalKnowledgeDataset(
        String datasetId,
        String version,
        String title,
        String description,
        String language,
        String sourceLanguage,
        LocalDate generatedAt,
        int recordCount,
        String embeddingTextField,
        String clinicalReviewStatus,
        String translationMethod,
        String curationNote,
        boolean requiresDatabaseServiceMapping,
        List<String> intendedUses,
        List<String> excludedContent,
        List<DentalKnowledgeRecord> records
) {

    public DentalKnowledgeDataset {
        intendedUses = List.copyOf(intendedUses);
        excludedContent = List.copyOf(excludedContent);
        records = List.copyOf(records);
    }
}
