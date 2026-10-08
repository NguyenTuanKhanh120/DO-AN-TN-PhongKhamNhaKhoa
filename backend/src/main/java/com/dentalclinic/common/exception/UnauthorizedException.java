package com.dentalclinic.common.exception;

import com.dentalclinic.common.constant.ErrorCode;

public class UnauthorizedException extends BusinessException {

    public UnauthorizedException() {
        super(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    public UnauthorizedException(ErrorCode errorCode) {
        super(errorCode);
        if (errorCode.getHttpStatus().value() != 401) {
            throw new IllegalArgumentException("UnauthorizedException requires an HTTP 401 error code");
        }
    }

    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
        if (errorCode.getHttpStatus().value() != 401) {
            throw new IllegalArgumentException("UnauthorizedException requires an HTTP 401 error code");
        }
    }
}
