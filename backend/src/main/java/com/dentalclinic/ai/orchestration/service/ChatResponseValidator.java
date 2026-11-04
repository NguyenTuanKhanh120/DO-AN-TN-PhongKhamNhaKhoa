package com.dentalclinic.ai.orchestration.service;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class ChatResponseValidator {

    private static final int MAX_RESPONSE_LENGTH = 8_000;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b"
    );
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(gemini_api_key|jwt_secret|db_password|refresh[_ -]?token|-----begin [a-z ]*private key-----)"
    );
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "(?i)\\beyJ[a-z0-9_-]{8,}\\.[a-z0-9_-]{8,}\\.[a-z0-9_-]{8,}\\b"
    );
    private static final Pattern GOOGLE_API_KEY_PATTERN = Pattern.compile(
            "\\bAIza[0-9A-Za-z_-]{35}\\b"
    );
    private static final Pattern MARKDOWN_BULLET_PATTERN = Pattern.compile("(?m)^\\s*[*+]\\s+");
    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("(?m)^\\s*#{1,6}\\s+");

    public String validate(String response) {
        if (response == null || response.isBlank()) {
            throw invalidResponse();
        }
        String normalized = response.strip();
        validateContent(normalized);
        return normalizeFormatting(normalized);
    }

    public void validatePartial(String response) {
        if (response == null) {
            throw invalidResponse();
        }
        validateContent(response);
    }

    private void validateContent(String response) {
        if (response.length() > MAX_RESPONSE_LENGTH
                || containsUnsafeControlCharacter(response)
                || UUID_PATTERN.matcher(response).find()
                || SECRET_PATTERN.matcher(response.toLowerCase(Locale.ROOT)).find()
                || JWT_PATTERN.matcher(response).find()
                || GOOGLE_API_KEY_PATTERN.matcher(response).find()) {
            throw invalidResponse();
        }
    }

    private boolean containsUnsafeControlCharacter(String value) {
        return value.codePoints().anyMatch(codePoint ->
                Character.isISOControl(codePoint)
                        && codePoint != '\n'
                        && codePoint != '\r'
                        && codePoint != '\t'
        );
    }

    private String normalizeFormatting(String value) {
        return MARKDOWN_HEADING_PATTERN.matcher(
                        MARKDOWN_BULLET_PATTERN.matcher(value).replaceAll("- ")
                ).replaceAll("")
                .replace("**", "")
                .replace("__", "")
                .replace("*", "")
                .replace("`", "")
                .strip();
    }

    private BusinessException invalidResponse() {
        return new BusinessException(
                ErrorCode.AI_SERVICE_UNAVAILABLE,
                "Trợ lý AI trả về nội dung không an toàn hoặc không hợp lệ"
        );
    }
}
