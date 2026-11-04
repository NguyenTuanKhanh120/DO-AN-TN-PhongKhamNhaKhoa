package com.dentalclinic.ai.gateway;

import com.google.genai.errors.ApiException;

public final class AiProviderFailureClassifier {

    public static final String QUOTA_EXCEEDED_MESSAGE =
            "Trợ lý AI đã đạt giới hạn sử dụng Gemini. Vui lòng thử lại sau.";

    private AiProviderFailureClassifier() {
    }

    public static boolean isQuotaExceeded(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ApiException apiException && apiException.code() == 429) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                return false;
            }
            current = cause;
        }
        return false;
    }
}
