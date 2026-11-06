package com.dentalclinic.ai.knowledge.parser;

import com.dentalclinic.ai.knowledge.exception.KnowledgeDatasetValidationException;
import com.dentalclinic.ai.knowledge.model.DentalKnowledgeDataset;
import com.dentalclinic.ai.knowledge.model.DentalKnowledgeRecord;
import com.dentalclinic.ai.knowledge.model.DentalKnowledgeSource;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class NidcrDentalKnowledgeDatasetValidator {

    public static final String EXPECTED_DATASET_ID = DentalKnowledgeDatasetProfile.NIDCR.datasetId();
    private static final String EXPECTED_EMBEDDING_TEXT_FIELD = "content";
    private static final Pattern SEMANTIC_VERSION = Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+$");
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Set<String> ROOT_FIELDS = Set.of(
            "datasetId",
            "version",
            "title",
            "description",
            "language",
            "sourceLanguage",
            "generatedAt",
            "recordCount",
            "embeddingTextField",
            "clinicalReviewStatus",
            "translationMethod",
            "curationNote",
            "requiresDatabaseServiceMapping",
            "intendedUses",
            "excludedContent",
            "records"
    );
    private static final Set<String> RECORD_FIELDS = Set.of(
            "id",
            "slug",
            "title",
            "category",
            "content",
            "symptoms",
            "possibleFactors",
            "careKnowledge",
            "serviceCategoryHints",
            "specializationHints",
            "keywords",
            "source"
    );
    private static final Set<String> SOURCE_FIELDS = Set.of(
            "publisher",
            "organization",
            "title",
            "url",
            "retrievedAt",
            "contentType"
    );

    public DentalKnowledgeDataset validate(JsonNode root) {
        requireObject(root, "$", "root phải là object JSON");
        rejectUnknownFields(root, ROOT_FIELDS, "$");

        String datasetId = requiredText(root, "datasetId", "$");
        DentalKnowledgeDatasetProfile profile = DentalKnowledgeDatasetProfile
                .fromDatasetId(datasetId)
                .orElseThrow(() -> invalid(
                        "$.datasetId",
                        "không thuộc danh sách dataset được hỗ trợ"
                ));

        String version = requiredText(root, "version", "$");
        if (!SEMANTIC_VERSION.matcher(version).matches()) {
            throw invalid("$.version", "phải có định dạng semantic version, ví dụ 1.0.0");
        }

        String language = requiredText(root, "language", "$");
        requireValue(language, profile.language(), "$.language");
        String sourceLanguage = requiredText(root, "sourceLanguage", "$");
        requireValue(sourceLanguage, profile.sourceLanguage(), "$.sourceLanguage");
        String embeddingTextField = requiredText(root, "embeddingTextField", "$");
        requireValue(embeddingTextField, EXPECTED_EMBEDDING_TEXT_FIELD, "$.embeddingTextField");

        int declaredRecordCount = requiredNonNegativeInteger(root, "recordCount", "$");
        List<DentalKnowledgeRecord> records = records(root.get("records"), profile);
        if (declaredRecordCount != records.size()) {
            throw invalid(
                    "$.recordCount",
                    "không khớp số record thực tế: khai báo "
                            + declaredRecordCount
                            + ", thực tế "
                            + records.size()
            );
        }

        return new DentalKnowledgeDataset(
                datasetId,
                version,
                requiredText(root, "title", "$"),
                requiredText(root, "description", "$"),
                language,
                sourceLanguage,
                requiredDate(root, "generatedAt", "$"),
                declaredRecordCount,
                embeddingTextField,
                requiredText(root, "clinicalReviewStatus", "$"),
                requiredText(root, "translationMethod", "$"),
                requiredText(root, "curationNote", "$"),
                requiredBoolean(root, "requiresDatabaseServiceMapping", "$"),
                requiredStringList(root, "intendedUses", "$"),
                requiredStringList(root, "excludedContent", "$"),
                records
        );
    }

    private List<DentalKnowledgeRecord> records(
            JsonNode recordsNode,
            DentalKnowledgeDatasetProfile profile
    ) {
        if (recordsNode == null || !recordsNode.isArray()) {
            throw invalid("$.records", "phải là array");
        }
        if (recordsNode.isEmpty()) {
            throw invalid("$.records", "không được rỗng");
        }

        List<DentalKnowledgeRecord> records = new ArrayList<>(recordsNode.size());
        Set<String> recordIds = new HashSet<>();
        Set<String> slugs = new HashSet<>();
        for (int index = 0; index < recordsNode.size(); index++) {
            String path = "$.records[" + index + "]";
            JsonNode recordNode = recordsNode.get(index);
            requireObject(recordNode, path, "record phải là object JSON");
            rejectUnknownFields(recordNode, RECORD_FIELDS, path);

            String id = requiredText(recordNode, "id", path);
            if (!profile.matchesRecordId(id)) {
                throw invalid(path + ".id", "phải có định dạng " + profile.recordIdFormat());
            }
            if (!recordIds.add(id)) {
                throw invalid(path + ".id", "bị trùng trong dataset: " + id);
            }

            String slug = requiredText(recordNode, "slug", path);
            if (!SLUG.matcher(slug).matches()) {
                throw invalid(path + ".slug", "chỉ được dùng chữ thường, số và dấu gạch ngang");
            }
            if (!slugs.add(slug)) {
                throw invalid(path + ".slug", "bị trùng trong dataset: " + slug);
            }

            records.add(new DentalKnowledgeRecord(
                    id,
                    slug,
                    requiredText(recordNode, "title", path),
                    requiredText(recordNode, "category", path),
                    requiredText(recordNode, "content", path),
                    requiredStringList(recordNode, "symptoms", path),
                    requiredStringList(recordNode, "possibleFactors", path),
                    requiredStringList(recordNode, "careKnowledge", path),
                    requiredStringList(recordNode, "serviceCategoryHints", path),
                    requiredStringList(recordNode, "specializationHints", path),
                    requiredStringList(recordNode, "keywords", path),
                    source(recordNode.get("source"), path + ".source")
            ));
        }
        return List.copyOf(records);
    }

    private DentalKnowledgeSource source(JsonNode sourceNode, String path) {
        requireObject(sourceNode, path, "source phải là object JSON");
        rejectUnknownFields(sourceNode, SOURCE_FIELDS, path);
        return new DentalKnowledgeSource(
                requiredText(sourceNode, "publisher", path),
                requiredText(sourceNode, "organization", path),
                requiredText(sourceNode, "title", path),
                requiredHttpsUri(sourceNode, "url", path),
                requiredDate(sourceNode, "retrievedAt", path),
                requiredText(sourceNode, "contentType", path)
        );
    }

    private String requiredText(JsonNode parent, String field, String parentPath) {
        JsonNode value = parent.get(field);
        String path = parentPath + "." + field;
        if (value == null) {
            throw invalid(path, "là trường bắt buộc");
        }
        if (!value.isTextual()) {
            throw invalid(path, "phải là string");
        }
        String normalized = normalize(value.textValue());
        if (normalized.isEmpty()) {
            throw invalid(path, "không được để trống");
        }
        return normalized;
    }

    private List<String> requiredStringList(JsonNode parent, String field, String parentPath) {
        JsonNode value = parent.get(field);
        String path = parentPath + "." + field;
        if (value == null) {
            throw invalid(path, "là trường bắt buộc");
        }
        if (!value.isArray()) {
            throw invalid(path, "phải là array string");
        }

        LinkedHashSet<String> normalizedValues = new LinkedHashSet<>();
        for (int index = 0; index < value.size(); index++) {
            JsonNode item = value.get(index);
            String itemPath = path + "[" + index + "]";
            if (!item.isTextual()) {
                throw invalid(itemPath, "phải là string");
            }
            String normalized = normalize(item.textValue());
            if (normalized.isEmpty()) {
                throw invalid(itemPath, "không được để trống");
            }
            normalizedValues.add(normalized);
        }
        return List.copyOf(normalizedValues);
    }

    private LocalDate requiredDate(JsonNode parent, String field, String parentPath) {
        String value = requiredText(parent, field, parentPath);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalid(parentPath + "." + field, "phải là ngày ISO-8601 theo định dạng yyyy-MM-dd");
        }
    }

    private URI requiredHttpsUri(JsonNode parent, String field, String parentPath) {
        String value = requiredText(parent, field, parentPath);
        try {
            URI uri = new URI(value).normalize();
            if (!uri.isAbsolute()
                    || !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw invalid(parentPath + "." + field, "phải là URL HTTPS tuyệt đối hợp lệ");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw invalid(parentPath + "." + field, "phải là URL HTTPS tuyệt đối hợp lệ");
        }
    }

    private int requiredNonNegativeInteger(JsonNode parent, String field, String parentPath) {
        JsonNode value = parent.get(field);
        String path = parentPath + "." + field;
        if (value == null) {
            throw invalid(path, "là trường bắt buộc");
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
            throw invalid(path, "phải là số nguyên không âm");
        }
        return value.intValue();
    }

    private boolean requiredBoolean(JsonNode parent, String field, String parentPath) {
        JsonNode value = parent.get(field);
        String path = parentPath + "." + field;
        if (value == null) {
            throw invalid(path, "là trường bắt buộc");
        }
        if (!value.isBoolean()) {
            throw invalid(path, "phải là boolean");
        }
        return value.booleanValue();
    }

    private void rejectUnknownFields(JsonNode node, Set<String> allowedFields, String path) {
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowedFields.contains(field)) {
                throw invalid(path + "." + field, "không thuộc schema được hỗ trợ");
            }
        }
    }

    private void requireObject(JsonNode node, String path, String message) {
        if (node == null || !node.isObject()) {
            throw invalid(path, message);
        }
    }

    private void requireValue(String actual, String expected, String path) {
        if (!expected.equals(actual)) {
            throw invalid(path, "phải có giá trị " + expected);
        }
    }

    private String normalize(String value) {
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
    }

    private KnowledgeDatasetValidationException invalid(String path, String reason) {
        return new KnowledgeDatasetValidationException(
                "Dataset nha khoa không hợp lệ tại " + path + ": " + reason
        );
    }
}
