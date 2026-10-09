package com.dentalclinic.common.constant;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    SUCCESS(HttpStatus.OK, "Thành công"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Nội dung yêu cầu không hợp lệ"),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập để truy cập tài nguyên này"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không chính xác"),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "Tài khoản đã bị vô hiệu hóa"),
    PASSWORD_TOO_LONG(HttpStatus.BAD_REQUEST, "Mật khẩu không được vượt quá 72 byte UTF-8"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT, "Dữ liệu bị xung đột"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email đã được sử dụng"),
    LICENSE_NUMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "Số giấy phép hành nghề đã được sử dụng"),
    SERVICE_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Tên dịch vụ đã được sử dụng"),
    SCHEDULE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Lịch làm việc cho ngày và buổi này đã tồn tại"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức HTTP không được hỗ trợ"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi hệ thống");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
