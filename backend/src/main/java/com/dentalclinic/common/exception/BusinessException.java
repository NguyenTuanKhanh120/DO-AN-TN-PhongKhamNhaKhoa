package com.dentalclinic.common.exception;

import com.dentalclinic.common.constant.ErrorCode;

import java.util.Objects;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(resolveMessage(errorCode, message));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    private static String resolveMessage(ErrorCode errorCode, String message) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        return message == null || message.isBlank() ? errorCode.getDefaultMessage() : message;
    }
}
