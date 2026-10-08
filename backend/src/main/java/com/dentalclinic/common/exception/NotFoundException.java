package com.dentalclinic.common.exception;

import com.dentalclinic.common.constant.ErrorCode;

public class NotFoundException extends BusinessException {

    public NotFoundException() {
        super(ErrorCode.RESOURCE_NOT_FOUND);
    }

    public NotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public NotFoundException(String resourceName, Object identifier) {
        super(
                ErrorCode.RESOURCE_NOT_FOUND,
                "%s không tồn tại với định danh %s".formatted(resourceName, identifier)
        );
    }
}
