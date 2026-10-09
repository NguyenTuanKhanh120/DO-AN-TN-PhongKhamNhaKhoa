package com.dentalclinic.medicalrecord.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PrescriptionItem(
        @NotBlank(message = "Tên thuốc không được để trống")
        String medicationName,

        @NotBlank(message = "Liều dùng không được để trống")
        String dosage,

        @NotNull(message = "Số lượng thuốc không được để trống")
        @Positive(message = "Số lượng thuốc phải lớn hơn 0")
        Integer quantity,

        @NotBlank(message = "Hướng dẫn sử dụng không được để trống")
        String instructions
) {
}
