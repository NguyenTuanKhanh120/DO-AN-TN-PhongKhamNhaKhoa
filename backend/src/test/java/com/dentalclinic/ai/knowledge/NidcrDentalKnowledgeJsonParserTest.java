package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.config.KnowledgeBaseProperties;
import com.dentalclinic.ai.knowledge.exception.KnowledgeDatasetValidationException;
import com.dentalclinic.ai.knowledge.model.DentalKnowledgeDataset;
import com.dentalclinic.ai.knowledge.parser.NidcrDentalKnowledgeDatasetValidator;
import com.dentalclinic.ai.knowledge.parser.NidcrDentalKnowledgeJsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NidcrDentalKnowledgeJsonParserTest {

    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

    @TempDir
    private Path tempDirectory;

    @Test
    void shouldParseAndNormalizeValidDataset() throws Exception {
        Path datasetFile = writeDataset(validDataset(validRecord("NIDCR-VI-001", "tooth-decay"), 1));

        DentalKnowledgeDataset dataset = parser(tempDirectory, DEFAULT_MAX_FILE_SIZE_BYTES)
                .parse(datasetFile);

        assertThat(dataset.datasetId()).isEqualTo("nidcr-dental-knowledge-vi");
        assertThat(dataset.version()).isEqualTo("1.0.0");
        assertThat(dataset.recordCount()).isOne();
        assertThat(dataset.clinicalReviewStatus()).isEqualTo("PENDING_CLINICAL_REVIEW");
        assertThat(dataset.records()).hasSize(1);
        assertThat(dataset.records().getFirst().title()).isEqualTo("Sâu răng");
        assertThat(dataset.records().getFirst().content()).isEqualTo("Nội dung hợp lệ");
        assertThat(dataset.records().getFirst().symptoms())
                .containsExactly("đau răng");
        assertThat(dataset.records().getFirst().source().url().toString())
                .isEqualTo("https://www.nidcr.nih.gov/health-info/tooth-decay");
    }

    @Test
    void shouldValidatePrimaryDatasetUsedByTheProject() {
        Path knowledgeBaseDirectory = locateProjectKnowledgeBase();

        DentalKnowledgeDataset dataset = parser(knowledgeBaseDirectory, DEFAULT_MAX_FILE_SIZE_BYTES)
                .parsePrimaryDataset();

        assertThat(dataset.datasetId()).isEqualTo("nidcr-dental-knowledge-vi");
        assertThat(dataset.version()).isEqualTo("1.0.0");
        assertThat(dataset.recordCount()).isEqualTo(16);
        assertThat(dataset.records()).hasSize(16);
        assertThat(dataset.records()).extracting(record -> record.id()).doesNotHaveDuplicates();
        assertThat(dataset.records()).extracting(record -> record.slug()).doesNotHaveDuplicates();
        assertThat(dataset.records()).allSatisfy(record -> {
            assertThat(record.content()).isNotBlank();
            assertThat(record.source().url()).hasScheme("https");
        });
    }

    @Test
    void shouldReportExactRecordPathWhenRequiredFieldIsMissing() throws Exception {
        String invalidRecord = validRecord("NIDCR-VI-001", "tooth-decay")
                .replace("\"content\": \" Nội dung hợp lệ \",\n", "");
        Path datasetFile = writeDataset(validDataset(invalidRecord, 1));

        assertThatThrownBy(() -> parser(tempDirectory, DEFAULT_MAX_FILE_SIZE_BYTES).parse(datasetFile))
                .isInstanceOf(KnowledgeDatasetValidationException.class)
                .hasMessageContaining("$.records[0].content")
                .hasMessageContaining("trường bắt buộc");
    }

    @Test
    void shouldRejectDeclaredRecordCountMismatch() throws Exception {
        Path datasetFile = writeDataset(validDataset(validRecord("NIDCR-VI-001", "tooth-decay"), 2));

        assertThatThrownBy(() -> parser(tempDirectory, DEFAULT_MAX_FILE_SIZE_BYTES).parse(datasetFile))
                .isInstanceOf(KnowledgeDatasetValidationException.class)
                .hasMessageContaining("$.recordCount")
                .hasMessageContaining("không khớp số record thực tế");
    }

    @Test
    void shouldRejectDuplicateRecordIdentity() throws Exception {
        String duplicatedRecords = validRecord("NIDCR-VI-001", "tooth-decay")
                + ","
                + validRecord("NIDCR-VI-001", "another-slug");
        Path datasetFile = writeDataset(validDataset(duplicatedRecords, 2));

        assertThatThrownBy(() -> parser(tempDirectory, DEFAULT_MAX_FILE_SIZE_BYTES).parse(datasetFile))
                .isInstanceOf(KnowledgeDatasetValidationException.class)
                .hasMessageContaining("$.records[1].id")
                .hasMessageContaining("bị trùng");
    }

    @Test
    void shouldRejectUnknownSchemaField() throws Exception {
        String invalidDataset = validDataset(validRecord("NIDCR-VI-001", "tooth-decay"), 1)
                .replace("\"version\": \"1.0.0\",", "\"version\": \"1.0.0\",\n  \"unexpected\": true,");
        Path datasetFile = writeDataset(invalidDataset);

        assertThatThrownBy(() -> parser(tempDirectory, DEFAULT_MAX_FILE_SIZE_BYTES).parse(datasetFile))
                .isInstanceOf(KnowledgeDatasetValidationException.class)
                .hasMessageContaining("$.unexpected")
                .hasMessageContaining("không thuộc schema");
    }

    @Test
    void shouldRejectDuplicateJsonKeys() throws Exception {
        String invalidDataset = validDataset(validRecord("NIDCR-VI-001", "tooth-decay"), 1)
                .replace(
                        "\"datasetId\": \"nidcr-dental-knowledge-vi\",",
                        "\"datasetId\": \"nidcr-dental-knowledge-vi\",\n"
                                + "  \"datasetId\": \"nidcr-dental-knowledge-vi\","
                );
        Path datasetFile = writeDataset(invalidDataset);

        assertThatThrownBy(() -> parser(tempDirectory, DEFAULT_MAX_FILE_SIZE_BYTES).parse(datasetFile))
                .isInstanceOf(KnowledgeDatasetValidationException.class)
                .hasMessageContaining("Cú pháp JSON không hợp lệ")
                .hasMessageContaining("dòng");
    }

    @Test
    void shouldRejectTrailingJsonContent() throws Exception {
        Path datasetFile = writeDataset(
                validDataset(validRecord("NIDCR-VI-001", "tooth-decay"), 1) + "{}"
        );

        assertThatThrownBy(() -> parser(tempDirectory, DEFAULT_MAX_FILE_SIZE_BYTES).parse(datasetFile))
                .isInstanceOf(KnowledgeDatasetValidationException.class)
                .hasMessageContaining("Cú pháp JSON không hợp lệ");
    }

    @Test
    void shouldRejectMalformedUtf8() throws Exception {
        Path datasetFile = tempDirectory.resolve("invalid-utf8.json");
        Files.write(datasetFile, new byte[]{(byte) 0xC3, 0x28});

        assertThatThrownBy(() -> parser(tempDirectory, DEFAULT_MAX_FILE_SIZE_BYTES).parse(datasetFile))
                .isInstanceOf(KnowledgeDatasetValidationException.class)
                .hasMessageContaining("UTF-8 hợp lệ");
    }

    @Test
    void shouldRejectFileLargerThanConfiguredLimit() throws Exception {
        Path datasetFile = writeDataset(validDataset(validRecord("NIDCR-VI-001", "tooth-decay"), 1));

        assertThatThrownBy(() -> parser(tempDirectory, 32).parse(datasetFile))
                .isInstanceOf(KnowledgeDatasetValidationException.class)
                .hasMessageContaining("vượt quá giới hạn 32 byte");
    }

    @Test
    void shouldRejectDatasetOutsideConfiguredTopLevelDirectory() throws Exception {
        Path nestedDirectory = Files.createDirectory(tempDirectory.resolve("nested"));
        Path datasetFile = nestedDirectory.resolve("dataset.json");
        Files.writeString(datasetFile, validDataset(validRecord("NIDCR-VI-001", "tooth-decay"), 1));

        assertThatThrownBy(() -> parser(tempDirectory, DEFAULT_MAX_FILE_SIZE_BYTES).parse(datasetFile))
                .isInstanceOf(KnowledgeDatasetValidationException.class)
                .hasMessageContaining("nằm trực tiếp trong thư mục kho kiến thức");
    }

    private NidcrDentalKnowledgeJsonParser parser(Path directory, long maxFileSizeBytes) {
        return new NidcrDentalKnowledgeJsonParser(
                new ObjectMapper(),
                new NidcrDentalKnowledgeDatasetValidator(),
                new KnowledgeBaseProperties(directory, maxFileSizeBytes)
        );
    }

    private Path locateProjectKnowledgeBase() {
        Path repositoryRootDirectory = Path.of("knowledge-base").toAbsolutePath().normalize();
        if (Files.isRegularFile(repositoryRootDirectory.resolve(
                NidcrDentalKnowledgeJsonParser.PRIMARY_DATASET_FILE_NAME
        ))) {
            return repositoryRootDirectory;
        }
        return Path.of("..", "knowledge-base").toAbsolutePath().normalize();
    }

    private Path writeDataset(String content) throws Exception {
        return Files.writeString(
                tempDirectory.resolve("dataset.json"),
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private String validDataset(String records, int recordCount) {
        return """
                {
                  "datasetId": "nidcr-dental-knowledge-vi",
                  "version": "1.0.0",
                  "title": "Dataset nha khoa",
                  "description": "Mô tả dataset",
                  "language": "vi",
                  "sourceLanguage": "en",
                  "generatedAt": "2026-10-28",
                  "recordCount": %d,
                  "embeddingTextField": "content",
                  "clinicalReviewStatus": "PENDING_CLINICAL_REVIEW",
                  "translationMethod": "AI_ASSISTED_PARAPHRASE",
                  "curationNote": "Ghi chú kiểm duyệt",
                  "requiresDatabaseServiceMapping": true,
                  "intendedUses": ["RAG_RETRIEVAL"],
                  "excludedContent": ["PATIENT_DATA"],
                  "records": [%s]
                }
                """.formatted(recordCount, records);
    }

    private String validRecord(String id, String slug) {
        return """
                {
                  "id": "%s",
                  "slug": "%s",
                  "title": " Sâu răng ",
                  "category": "DENTAL_CONDITION",
                  "content": " Nội dung hợp lệ ",
                  "symptoms": [" đau răng ", "đau răng"],
                  "possibleFactors": ["mảng bám"],
                  "careKnowledge": ["vệ sinh răng miệng"],
                  "serviceCategoryHints": ["khám nha khoa"],
                  "specializationHints": ["nha khoa tổng quát"],
                  "keywords": ["sâu răng"],
                  "source": {
                    "publisher": "NIDCR",
                    "organization": "NIH",
                    "title": "Tooth Decay",
                    "url": "https://www.nidcr.nih.gov/health-info/tooth-decay",
                    "retrievedAt": "2026-10-28",
                    "contentType": "OFFICIAL_HEALTH_INFORMATION"
                  }
                }
                """.formatted(id, slug);
    }
}
