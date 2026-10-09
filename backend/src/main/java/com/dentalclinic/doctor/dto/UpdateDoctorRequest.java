package com.dentalclinic.doctor.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateDoctorRequest(
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

        @NotNull(message = "Đánh giá không được để trống")
        @DecimalMin(value = "0.0", message = "Đánh giá phải lớn hơn hoặc bằng 0")
        @DecimalMax(value = "5.0", message = "Đánh giá phải nhỏ hơn hoặc bằng 5")
        @Digits(integer = 1, fraction = 1, message = "Đánh giá chỉ được có tối đa một chữ số thập phân")
        BigDecimal rating,

        @NotNull(message = "Số năm kinh nghiệm không được để trống")
        @Min(value = 0, message = "Số năm kinh nghiệm không được âm")
        Integer experienceYears
) {
}
