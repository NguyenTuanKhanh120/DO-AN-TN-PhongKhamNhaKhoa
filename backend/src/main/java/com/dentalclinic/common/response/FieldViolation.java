package com.dentalclinic.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Chi tiết lỗi validation của một field")
public record FieldViolation(
        @Schema(example = "email")
        String field,
        @Schema(example = "Email không đúng định dạng")
        String message
) {
}
