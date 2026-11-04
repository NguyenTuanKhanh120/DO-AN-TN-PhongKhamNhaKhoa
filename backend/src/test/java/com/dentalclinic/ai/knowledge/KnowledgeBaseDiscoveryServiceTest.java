package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.config.KnowledgeBaseProperties;
import com.dentalclinic.ai.knowledge.entity.KnowledgeFileType;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.service.KnowledgeBaseDiscoveryService;
import com.dentalclinic.common.constant.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeBaseDiscoveryServiceTest {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

    @TempDir
    private Path tempDirectory;

    @Test
    void shouldDiscoverOnlyTopLevelJsonAndCsvFilesInStableOrder() throws Exception {
        Path csv = Files.writeString(tempDirectory.resolve("alpha.csv"), "question,answer\nA,B");
        Path json = Files.writeString(tempDirectory.resolve("Beta.JSON"), "{}");
        Files.writeString(tempDirectory.resolve("notes.txt"), "ignored");
        Files.writeString(tempDirectory.resolve(".json"), "ignored");
        Path nestedDirectory = Files.createDirectory(tempDirectory.resolve("nested"));
        Files.writeString(nestedDirectory.resolve("nested.json"), "{}");

        KnowledgeBaseDiscoveryService service = new KnowledgeBaseDiscoveryService(
                new KnowledgeBaseProperties(tempDirectory, MAX_FILE_SIZE_BYTES)
        );

        var files = service.discover();

        assertThat(files).extracting(file -> file.fileName())
                .containsExactly("alpha.csv", "Beta.JSON");
        assertThat(files).extracting(file -> file.fileType())
                .containsExactly(KnowledgeFileType.CSV, KnowledgeFileType.JSON);
        assertThat(files.get(0).path()).isEqualTo(csv.toAbsolutePath().normalize());
        assertThat(files.get(1).path()).isEqualTo(json.toAbsolutePath().normalize());
        assertThat(files).allSatisfy(file -> {
            assertThat(file.sizeBytes()).isPositive();
            assertThat(file.lastModifiedAt()).isNotNull();
        });
    }

    @Test
    void shouldReturnEmptyListForReadableDirectoryWithoutSupportedFiles() throws Exception {
        Files.writeString(tempDirectory.resolve("readme.md"), "No dataset");

        var files = new KnowledgeBaseDiscoveryService(
                new KnowledgeBaseProperties(tempDirectory, MAX_FILE_SIZE_BYTES)
        ).discover();

        assertThat(files).isEmpty();
    }

    @Test
    void shouldRejectMissingKnowledgeBaseDirectory() {
        Path missingDirectory = tempDirectory.resolve("missing");
        KnowledgeBaseDiscoveryService service = new KnowledgeBaseDiscoveryService(
                new KnowledgeBaseProperties(missingDirectory, MAX_FILE_SIZE_BYTES)
        );

        assertThatThrownBy(service::discover)
                .isInstanceOfSatisfying(KnowledgeBaseException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.KNOWLEDGE_BASE_UNAVAILABLE);
                    assertThat(exception.getMessage())
                            .isEqualTo("Thư mục kho kiến thức không tồn tại hoặc không thể đọc");
                });
    }
}
