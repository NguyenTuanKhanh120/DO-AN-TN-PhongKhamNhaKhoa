package com.dentalclinic.common.response;

import com.dentalclinic.common.constant.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Envelope thống nhất cho mọi REST response")
public record ApiResponse<T>(
        @Schema(description = "Kết quả xử lý", example = "true")
        boolean success,
        @Schema(description = "Mã kết quả hoặc mã lỗi", example = "SUCCESS")
        String code,
        @Schema(description = "Thông báo cho client", example = "Thành công")
        String message,
        @Schema(description = "Payload thành công; null khi có lỗi")
        T data,
        @Schema(description = "Danh sách lỗi field; chỉ xuất hiện khi validation thất bại")
        List<FieldViolation> errors,
        @Schema(description = "Request path gây lỗi", example = "/api/patient/appointments")
        String path
) {

    public ApiResponse {
        errors = errors == null ? null : List.copyOf(errors);
    }

    public static ApiResponse<Void> emptySuccess() {
        return success(ErrorCode.SUCCESS.getDefaultMessage(), null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(ErrorCode.SUCCESS.getDefaultMessage(), data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(
                true,
                ErrorCode.SUCCESS.name(),
                normalizeMessage(message, ErrorCode.SUCCESS.getDefaultMessage()),
                data,
                null,
                null
        );
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message, String path) {
        return error(errorCode, message, null, path);
    }

    public static ApiResponse<Void> error(
            ErrorCode errorCode,
            String message,
            List<FieldViolation> errors,
            String path
    ) {
        return new ApiResponse<>(
                false,
                errorCode.name(),
                normalizeMessage(message, errorCode.getDefaultMessage()),
                null,
                errors,
                path
        );
    }

    private static String normalizeMessage(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }
}
