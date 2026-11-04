package com.dentalclinic.ai.orchestration;

import com.dentalclinic.ai.orchestration.service.ChatResponseValidator;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatResponseValidatorTest {

    private final ChatResponseValidator validator = new ChatResponseValidator();

    @Test
    void shouldNormalizeSafeVietnameseResponse() {
        assertThat(validator.validate("  Bạn nên khám nha khoa để được đánh giá trực tiếp.  "))
                .isEqualTo("Bạn nên khám nha khoa để được đánh giá trực tiếp.");
    }

    @Test
    void shouldNormalizeCommonMarkdownMarkersToPlainText() {
        assertThat(validator.validate("""
                ### Nguyên nhân
                *   **Sâu răng:** Có thể gây đau nhức.
                *   **Bệnh nha chu:** Có thể gây đau vùng hàm.
                """))
                .isEqualTo("""
                        Nguyên nhân
                        - Sâu răng: Có thể gây đau nhức.
                        - Bệnh nha chu: Có thể gây đau vùng hàm.""");
    }

    @Test
    void shouldRejectBlankSecretMarkerInternalIdAndControlCharacter() {
        assertInvalid("   ");
        assertInvalid("Giá trị DB_PASSWORD là bí mật");
        assertInvalid("Bác sĩ có ID 4fd0d15d-a931-4b2e-8c1a-75e3c43c8e22");
        assertInvalid("Token: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJwYXRpZW50In0.signature12345678");
        assertInvalid("Khóa: " + "AIza" + "a".repeat(35));
        assertInvalid("Nội dung\u0000không hợp lệ");
    }

    @Test
    void shouldValidateCumulativeStreamingContentWithoutRejectingWhitespaceChunk() {
        validator.validatePartial(" ");
        validator.validatePartial("Câu trả lời an toàn");

        assertThatThrownBy(() -> validator.validatePartial("Nội dung DB_PASSWORD"))
                .isInstanceOf(BusinessException.class);
    }

    private void assertInvalid(String value) {
        assertThatThrownBy(() -> validator.validate(value))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE)
                );
    }
}
