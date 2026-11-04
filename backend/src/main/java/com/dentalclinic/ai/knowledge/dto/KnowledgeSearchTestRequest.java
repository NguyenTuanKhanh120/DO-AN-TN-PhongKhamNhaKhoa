package com.dentalclinic.ai.knowledge.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeSearchTestRequest(
        @NotBlank(message = "Câu truy vấn không được để trống")
        @Size(max = 1000, message = "Câu truy vấn không được vượt quá 1000 ký tự")
        String query,

        @Min(value = 1, message = "Top-K phải lớn hơn hoặc bằng 1")
        Integer topK,

        @DecimalMin(value = "0.0", message = "Độ tương đồng tối thiểu phải từ 0 đến 1")
        @DecimalMax(value = "1.0", message = "Độ tương đồng tối thiểu phải từ 0 đến 1")
        Double minimumSimilarity,

        @Valid KnowledgeSearchFilterRequest filter
) {
}
