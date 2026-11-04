package com.dentalclinic.ai.knowledge.dto;

import com.dentalclinic.ai.knowledge.entity.KnowledgeFileType;
import com.dentalclinic.ai.knowledge.entity.KnowledgeSourceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Trạng thái đồng bộ của một nguồn kiến thức do hệ thống quản lý")
public record KnowledgeSourceStatusResponse(
        String sourceKey,
        String fileName,
        KnowledgeFileType fileType,
        String sourceVersion,
        KnowledgeSourceStatus status,
        int chunkCount,
        String embeddingModel,
        Integer embeddingDimension,
        String clinicalReviewStatus,
        Instant lastSyncedAt,
        Instant lastSuccessAt,
        String lastError
) {
}
