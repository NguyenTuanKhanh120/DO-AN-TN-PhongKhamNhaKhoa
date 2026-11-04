package com.dentalclinic.ai.knowledge.service;

import com.dentalclinic.ai.knowledge.config.KnowledgeBaseProperties;
import com.dentalclinic.ai.knowledge.entity.KnowledgeFileType;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.model.KnowledgeSourceFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class KnowledgeBaseDiscoveryService {

    private final Path knowledgeBaseDirectory;

    public KnowledgeBaseDiscoveryService(KnowledgeBaseProperties properties) {
        this.knowledgeBaseDirectory = properties.directory().toAbsolutePath().normalize();
    }

    public List<KnowledgeSourceFile> discover() {
        requireReadableDirectory();
        try (Stream<Path> files = Files.list(knowledgeBaseDirectory)) {
            return files
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> toCandidate(path, resolveFileType(path)))
                    .flatMap(Optional::stream)
                    .sorted(Comparator
                            .comparing(KnowledgeSourceFile::fileName, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(KnowledgeSourceFile::fileName))
                    .toList();
        } catch (IOException exception) {
            throw new KnowledgeBaseException("Không thể đọc thư mục kho kiến thức đã cấu hình");
        }
    }

    private Optional<KnowledgeSourceFile> toCandidate(
            Path path,
            Optional<KnowledgeFileType> fileType
    ) {
        if (fileType.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new KnowledgeSourceFile(
                    path,
                    path.getFileName().toString(),
                    fileType.get(),
                    Files.size(path),
                    Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis())
            ));
        } catch (IOException exception) {
            throw new KnowledgeBaseException("Không thể đọc thông tin file trong kho kiến thức");
        }
    }

    private Optional<KnowledgeFileType> resolveFileType(Path path) {
        String fileName = path.getFileName().toString();
        int extensionSeparator = fileName.lastIndexOf('.');
        if (extensionSeparator <= 0 || extensionSeparator == fileName.length() - 1) {
            return Optional.empty();
        }
        return switch (fileName.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT)) {
            case "json" -> Optional.of(KnowledgeFileType.JSON);
            case "csv" -> Optional.of(KnowledgeFileType.CSV);
            default -> Optional.empty();
        };
    }

    private void requireReadableDirectory() {
        if (!Files.isDirectory(knowledgeBaseDirectory, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(knowledgeBaseDirectory)) {
            throw new KnowledgeBaseException("Thư mục kho kiến thức không tồn tại hoặc không thể đọc");
        }
    }
}
