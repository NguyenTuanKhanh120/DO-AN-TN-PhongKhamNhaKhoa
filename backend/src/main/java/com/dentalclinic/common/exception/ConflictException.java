package com.dentalclinic.common.exception;

import com.dentalclinic.common.constant.ErrorCode;

public class ConflictException extends BusinessException {

    public ConflictException() {
        super(ErrorCode.RESOURCE_CONFLICT);
    }

    public ConflictException(String message) {
        super(ErrorCode.RESOURCE_CONFLICT, message);
    }
}
