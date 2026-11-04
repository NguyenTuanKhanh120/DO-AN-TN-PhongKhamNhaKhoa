package com.dentalclinic.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Một phần nội dung câu trả lời đang được sinh")
public record ChatStreamTokenEvent(
        String content
) {
}
