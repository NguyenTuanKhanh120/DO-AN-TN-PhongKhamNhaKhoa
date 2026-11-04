package com.dentalclinic.ai.knowledge.dto;

import com.dentalclinic.ai.knowledge.model.KnowledgeSyncOutcome;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Kết quả yêu cầu đồng bộ thủ công dataset nha khoa chính")
public record KnowledgeSyncResponse(
        KnowledgeSyncOutcome outcome,
        Instant processedAt
) {
}
