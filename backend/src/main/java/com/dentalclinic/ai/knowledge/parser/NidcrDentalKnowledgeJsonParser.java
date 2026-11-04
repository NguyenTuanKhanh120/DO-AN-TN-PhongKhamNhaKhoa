package com.dentalclinic.ai.knowledge.parser;

import com.dentalclinic.ai.knowledge.config.KnowledgeBaseProperties;
import com.dentalclinic.ai.knowledge.exception.KnowledgeDatasetValidationException;
import com.dentalclinic.ai.knowledge.model.DentalKnowledgeDataset;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;

@Component
public class NidcrDentalKnowledgeJsonParser {

    public static final String PRIMARY_DATASET_FILE_NAME = "nidcr_dental_knowledge_vi.json";

    private final ObjectMapper objectMapper;
    private final NidcrDentalKnowledgeDatasetValidator validator;
    private final Path knowledgeBaseDirectory;
    private final long maxFileSizeBytes;

    public NidcrDentalKnowledgeJsonParser(
            ObjectMapper objectMapper,
            NidcrDentalKnowledgeDatasetValidator validator,
            KnowledgeBaseProperties properties
    ) {
        this.objectMapper = objectMapper.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.validator = validator;
        this.knowledgeBaseDirectory = properties.directory().toAbsolutePath().normalize();
        this.maxFileSizeBytes = properties.maxFileSizeBytes();
    }

    public DentalKnowledgeDataset parsePrimaryDataset() {
        return parse(knowledgeBaseDirectory.resolve(PRIMARY_DATASET_FILE_NAME));
    }

    public DentalKnowledgeDataset parse(Path datasetFile) {
        Path normalizedFile = validateDatasetFile(datasetFile);
        byte[] content = readBytes(normalizedFile);
        String json = decodeUtf8(content);
        try {
            JsonNode root = objectMapper.readTree(json);
            return validator.validate(root);
        } catch (JsonProcessingException exception) {
            throw malformedJson(exception);
        }
    }

    private Path validateDatasetFile(Path datasetFile) {
        if (datasetFile == null) {
            throw invalid("Đường dẫn dataset không được để trống");
        }

        Path normalizedFile = datasetFile.toAbsolutePath().normalize();
        if (!knowledgeBaseDirectory.equals(normalizedFile.getParent())) {
            throw invalid("Dataset phải nằm trực tiếp trong thư mục kho kiến thức đã cấu hình");
        }
        if (!normalizedFile.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw invalid("Parser AI-04 chỉ hỗ trợ dataset JSON");
        }
        if (Files.isSymbolicLink(normalizedFile)
                || !Files.isRegularFile(normalizedFile, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(normalizedFile)) {
            throw invalid("Dataset không tồn tại hoặc không thể đọc");
        }
        try {
            long fileSize = Files.size(normalizedFile);
            if (fileSize > maxFileSizeBytes) {
                throw invalid("Dataset vượt quá giới hạn " + maxFileSizeBytes + " byte");
            }
        } catch (IOException exception) {
            throw invalid("Không thể kiểm tra kích thước dataset");
        }
        return normalizedFile;
    }

    private byte[] readBytes(Path datasetFile) {
        try {
            byte[] content = Files.readAllBytes(datasetFile);
            if (content.length > maxFileSizeBytes) {
                throw invalid("Dataset vượt quá giới hạn " + maxFileSizeBytes + " byte");
            }
            return content;
        } catch (IOException exception) {
            throw invalid("Không thể đọc dataset");
        }
    }

    private String decodeUtf8(byte[] content) {
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            return decoded.startsWith("\uFEFF") ? decoded.substring(1) : decoded;
        } catch (CharacterCodingException exception) {
            throw invalid("Dataset phải được mã hóa UTF-8 hợp lệ");
        }
    }

    private KnowledgeDatasetValidationException malformedJson(JsonProcessingException exception) {
        JsonLocation location = exception.getLocation();
        String locationDescription = location == null
                ? ""
                : " tại dòng " + location.getLineNr() + ", cột " + location.getColumnNr();
        return invalid("Cú pháp JSON không hợp lệ" + locationDescription);
    }

    private KnowledgeDatasetValidationException invalid(String message) {
        return new KnowledgeDatasetValidationException(message);
    }
}
