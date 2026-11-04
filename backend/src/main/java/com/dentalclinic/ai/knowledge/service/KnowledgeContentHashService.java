package com.dentalclinic.ai.knowledge.service;

import com.dentalclinic.ai.knowledge.config.KnowledgeBaseProperties;
import com.dentalclinic.ai.knowledge.exception.KnowledgeBaseException;
import com.dentalclinic.ai.knowledge.model.KnowledgeSourceFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class KnowledgeContentHashService {

    private static final int BUFFER_SIZE = 8192;

    private final Path knowledgeBaseDirectory;
    private final long maxFileSizeBytes;

    public KnowledgeContentHashService(KnowledgeBaseProperties properties) {
        this.knowledgeBaseDirectory = properties.directory().toAbsolutePath().normalize();
        this.maxFileSizeBytes = properties.maxFileSizeBytes();
    }

    public String sha256(KnowledgeSourceFile sourceFile) {
        Path sourcePath = requireSafeSourceFile(sourceFile);
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[BUFFER_SIZE];
        long totalBytes = 0;
        try (InputStream inputStream = Files.newInputStream(sourcePath)) {
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                totalBytes += bytesRead;
                if (totalBytes > maxFileSizeBytes) {
                    throw unavailable("Dataset vượt quá giới hạn " + maxFileSizeBytes + " byte");
                }
                digest.update(buffer, 0, bytesRead);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw unavailable("Không thể tính SHA-256 cho dataset");
        }
    }

    private Path requireSafeSourceFile(KnowledgeSourceFile sourceFile) {
        if (sourceFile == null) {
            throw unavailable("Không tìm thấy dataset cần đồng bộ");
        }
        Path sourcePath = sourceFile.path().toAbsolutePath().normalize();
        if (!knowledgeBaseDirectory.equals(sourcePath.getParent())
                || Files.isSymbolicLink(sourcePath)
                || !Files.isRegularFile(sourcePath, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(sourcePath)) {
            throw unavailable("Dataset không tồn tại hoặc không thể đọc an toàn");
        }
        try {
            if (Files.size(sourcePath) > maxFileSizeBytes) {
                throw unavailable("Dataset vượt quá giới hạn " + maxFileSizeBytes + " byte");
            }
        } catch (IOException exception) {
            throw unavailable("Không thể kiểm tra kích thước dataset");
        }
        return sourcePath;
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw unavailable("JVM không hỗ trợ SHA-256");
        }
    }

    private KnowledgeBaseException unavailable(String message) {
        return new KnowledgeBaseException(message);
    }
}
