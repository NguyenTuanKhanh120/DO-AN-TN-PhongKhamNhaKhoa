package com.dentalclinic.ai.gateway;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;

public class AiCircuitOpenException extends BusinessException {

    public AiCircuitOpenException() {
        super(
                ErrorCode.AI_SERVICE_UNAVAILABLE,
                AiProviderFailureClassifier.SYSTEM_ERROR_MESSAGE
        );
    }
}
