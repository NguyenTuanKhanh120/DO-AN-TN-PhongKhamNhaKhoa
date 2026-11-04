package com.dentalclinic.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lỗi an toàn trong quá trình streaming")
public record ChatStreamErrorEvent(
        String code,
        String message,
        boolean partial
) {
}
