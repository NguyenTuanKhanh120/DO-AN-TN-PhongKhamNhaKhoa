package com.dentalclinic.ai.knowledge.exception;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;

public class KnowledgeBaseException extends BusinessException {

    public KnowledgeBaseException(String message) {
        super(ErrorCode.KNOWLEDGE_BASE_UNAVAILABLE, message);
    }
}
