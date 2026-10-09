package com.dentalclinic.doctor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDoctorRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255, message = "Email không được vượt quá 255 ký tự")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu phải có từ 8 đến 72 ký tự")
        String password,

        @NotBlank(message = "Họ tên không được để trống")
        @Size(min = 2, max = 150, message = "Họ tên phải có từ 2 đến 150 ký tự")
        String fullName,

        @NotBlank(message = "Chuyên khoa không được để trống")
        @Size(max = 150, message = "Chuyên khoa không được vượt quá 150 ký tự")
        String specialization,

        @NotBlank(message = "Số giấy phép hành nghề không được để trống")
        @Size(max = 100, message = "Số giấy phép hành nghề không được vượt quá 100 ký tự")
        String licenseNumber,

        @Size(max = 5000, message = "Tiểu sử không được vượt quá 5000 ký tự")
        String bio,

        @NotNull(message = "Số năm kinh nghiệm không được để trống")
        @Min(value = 0, message = "Số năm kinh nghiệm không được âm")
        Integer experienceYears
) {
}
