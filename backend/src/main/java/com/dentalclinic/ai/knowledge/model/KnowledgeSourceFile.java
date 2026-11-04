package com.dentalclinic.ai.knowledge.model;

import com.dentalclinic.ai.knowledge.entity.KnowledgeFileType;

import java.nio.file.Path;
import java.time.Instant;

public record KnowledgeSourceFile(
        Path path,
        String fileName,
        KnowledgeFileType fileType,
        long sizeBytes,
        Instant lastModifiedAt
) {

    public KnowledgeSourceFile {
        path = path.toAbsolutePath().normalize();
    }
}
