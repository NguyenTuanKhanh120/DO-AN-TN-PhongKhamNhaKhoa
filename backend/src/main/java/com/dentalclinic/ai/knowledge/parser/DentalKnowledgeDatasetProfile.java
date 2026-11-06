package com.dentalclinic.ai.knowledge.parser;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

public enum DentalKnowledgeDatasetProfile {

    NIDCR(
            "nidcr_dental_knowledge_vi.json",
            "nidcr-dental-knowledge-vi",
            "vi",
            "en",
            Pattern.compile("^NIDCR-VI-[0-9]{3}$"),
            "NIDCR-VI-NNN"
    ),
    VIETNAMESE_DENTAL_QA(
            "vietnamese_dental_qa_rag.json",
            "vietnamese-dental-qa-rag",
            "vi",
            "vi",
            Pattern.compile("^VN-DENTAL-QA-[0-9]{6}$"),
            "VN-DENTAL-QA-NNNNNN"
    );

    private final String fileName;
    private final String datasetId;
    private final String language;
    private final String sourceLanguage;
    private final Pattern recordIdPattern;
    private final String recordIdFormat;

    DentalKnowledgeDatasetProfile(
            String fileName,
            String datasetId,
            String language,
            String sourceLanguage,
            Pattern recordIdPattern,
            String recordIdFormat
    ) {
        this.fileName = fileName;
        this.datasetId = datasetId;
        this.language = language;
        this.sourceLanguage = sourceLanguage;
        this.recordIdPattern = recordIdPattern;
        this.recordIdFormat = recordIdFormat;
    }

    public String fileName() {
        return fileName;
    }

    public String datasetId() {
        return datasetId;
    }

    public String language() {
        return language;
    }

    public String sourceLanguage() {
        return sourceLanguage;
    }

    public boolean matchesRecordId(String recordId) {
        return recordIdPattern.matcher(recordId).matches();
    }

    public String recordIdFormat() {
        return recordIdFormat;
    }

    public static Optional<DentalKnowledgeDatasetProfile> fromFileName(String fileName) {
        return Arrays.stream(values())
                .filter(profile -> profile.fileName.equals(fileName))
                .findFirst();
    }

    public static Optional<DentalKnowledgeDatasetProfile> fromDatasetId(String datasetId) {
        return Arrays.stream(values())
                .filter(profile -> profile.datasetId.equals(datasetId))
                .findFirst();
    }
}
