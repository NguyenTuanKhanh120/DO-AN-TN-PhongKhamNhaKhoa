package com.dentalclinic.ai.knowledge.dto;

import jakarta.validation.constraints.Size;

public record KnowledgeSearchFilterRequest(
        @Size(max = 150, message = "Source key không được vượt quá 150 ký tự")
        String sourceKey,

        @Size(max = 100, message = "Danh mục không được vượt quá 100 ký tự")
        String category,

        @Size(max = 20, message = "Ngôn ngữ không được vượt quá 20 ký tự")
        String language,

        @Size(max = 50, message = "Trạng thái duyệt không được vượt quá 50 ký tự")
        String clinicalReviewStatus
) {
}
