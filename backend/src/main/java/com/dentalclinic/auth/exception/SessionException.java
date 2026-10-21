package com.dentalclinic.auth.exception;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;

public class SessionException extends BusinessException {

    public SessionException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SessionException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
