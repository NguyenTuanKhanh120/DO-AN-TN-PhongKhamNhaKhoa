package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.config.KnowledgeBaseProperties;
import com.dentalclinic.ai.knowledge.entity.KnowledgeFileType;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.model.KnowledgeSourceFile;
import com.dentalclinic.ai.knowledge.service.KnowledgeContentHashService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeContentHashServiceTest {

    @TempDir
    private Path directory;

    @Test
    void shouldCalculateLowercaseSha256WithoutLoadingUnboundedContent() throws Exception {
        Path file = Files.writeString(directory.resolve("dataset.json"), "abc");
        KnowledgeContentHashService service = service(1024);

        String contentHash = service.sha256(sourceFile(file));

        assertThat(contentHash)
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void shouldRejectFileThatExceedsConfiguredLimitBeforeHashing() throws Exception {
        Path file = Files.writeString(directory.resolve("dataset.json"), "too-large");
        KnowledgeContentHashService service = service(3);

        assertThatThrownBy(() -> service.sha256(sourceFile(file)))
                .isInstanceOf(KnowledgeBaseException.class)
                .hasMessageContaining("vượt quá giới hạn");
    }

    @Test
    void shouldRejectFileOutsideConfiguredKnowledgeDirectory() throws Exception {
        Path outsideDirectory = Files.createDirectory(directory.resolve("nested"));
        Path file = Files.writeString(outsideDirectory.resolve("dataset.json"), "abc");
        KnowledgeContentHashService service = service(1024);

        assertThatThrownBy(() -> service.sha256(sourceFile(file)))
                .isInstanceOf(KnowledgeBaseException.class)
                .hasMessageContaining("đọc an toàn");
    }

    private KnowledgeContentHashService service(long maxFileSizeBytes) {
        return new KnowledgeContentHashService(
                new KnowledgeBaseProperties(directory, maxFileSizeBytes)
        );
    }

    private KnowledgeSourceFile sourceFile(Path file) throws Exception {
        return new KnowledgeSourceFile(
                file,
                file.getFileName().toString(),
                KnowledgeFileType.JSON,
                Files.size(file),
                Instant.now()
        );
    }
}
