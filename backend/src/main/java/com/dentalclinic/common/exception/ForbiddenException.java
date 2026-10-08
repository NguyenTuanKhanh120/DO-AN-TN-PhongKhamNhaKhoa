package com.dentalclinic.common.exception;

import com.dentalclinic.common.constant.ErrorCode;

public class ForbiddenException extends BusinessException {

    public ForbiddenException() {
        super(ErrorCode.ACCESS_DENIED);
    }

    public ForbiddenException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }
}
