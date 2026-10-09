package com.dentalclinic.auth.security;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;

import java.nio.charset.StandardCharsets;

public final class BcryptPasswordValidator {

    private static final int MAX_PASSWORD_BYTES = 72;

    private BcryptPasswordValidator() {
    }

    public static void validate(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_LONG);
        }
    }
}
