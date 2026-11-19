package com.dentalclinic.ai.gateway;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.google.genai.errors.ApiException;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

public final class AiProviderFailureClassifier {

    public static final String SYSTEM_ERROR_MESSAGE =
            "hệ thống lỗi vui lòng thử lại sau";

    private AiProviderFailureClassifier() {
    }

    public static boolean isQuotaExceeded(Throwable failure) {
        return classify(failure) == FailureType.QUOTA_EXCEEDED;
    }

    public static FailureType classify(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ApiException apiException) {
                return switch (apiException.code()) {
                    case 400 -> FailureType.BAD_REQUEST;
                    case 401, 403 -> FailureType.AUTHENTICATION;
                    case 429 -> FailureType.QUOTA_EXCEEDED;
                    case 500, 502, 503, 504 -> FailureType.UNAVAILABLE;
                    default -> FailureType.PROVIDER_ERROR;
                };
            }
            if (current instanceof TimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof InterruptedIOException) {
                return FailureType.TIMEOUT;
            }
            if (current instanceof BusinessException businessException
                    && businessException.getErrorCode() == ErrorCode.AI_SERVICE_UNAVAILABLE) {
                return FailureType.AI_SERVICE_UNAVAILABLE;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return FailureType.UNKNOWN;
    }

    public enum FailureType {
        BAD_REQUEST,
        AUTHENTICATION,
        QUOTA_EXCEEDED,
        UNAVAILABLE,
        TIMEOUT,
        AI_SERVICE_UNAVAILABLE,
        PROVIDER_ERROR,
        UNKNOWN
    }
}
