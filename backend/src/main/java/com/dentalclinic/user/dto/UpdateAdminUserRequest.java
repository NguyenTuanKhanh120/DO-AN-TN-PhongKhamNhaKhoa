package com.dentalclinic.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAdminUserRequest(
        @NotBlank(message = "Họ và tên không được để trống")
        @Size(min = 2, max = 150, message = "Họ và tên phải có từ 2 đến 150 ký tự")
        String fullName,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255, message = "Email không được vượt quá 255 ký tự")
        String email
) {
    public UpdateAdminUserRequest {
        fullName = fullName == null ? null : fullName.trim();
        email = email == null ? null : email.trim();
    }
}
