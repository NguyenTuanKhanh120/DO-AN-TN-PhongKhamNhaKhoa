package com.dentalclinic.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendChatMessageRequest(
        @NotBlank(message = "Nội dung tin nhắn không được để trống")
        @Size(max = 2000, message = "Nội dung tin nhắn không được vượt quá 2000 ký tự")
        String message
) {
}
