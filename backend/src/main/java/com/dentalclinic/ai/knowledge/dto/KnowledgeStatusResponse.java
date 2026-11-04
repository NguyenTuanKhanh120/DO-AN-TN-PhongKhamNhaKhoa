package com.dentalclinic.ai.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Tổng quan trạng thái kho tri thức RAG")
public record KnowledgeStatusResponse(
        boolean aiEnabled,
        boolean ready,
        long totalSources,
        long successfulSources,
        long syncingSources,
        long failedSources,
        long pendingSources,
        long totalChunks,
        Instant lastSuccessfulSyncAt,
        List<KnowledgeSourceStatusResponse> sources
) {

    public KnowledgeStatusResponse {
        sources = List.copyOf(sources);
    }
}
