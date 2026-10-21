package com.dentalclinic.medicalrecord.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record MedicalRecordDraftPrescriptionItem(
        @Size(max = 300, message = "Tên thuốc không được vượt quá 300 ký tự")
        String medicationName,

        @Size(max = 300, message = "Liều dùng không được vượt quá 300 ký tự")
        String dosage,

        @Min(value = 1, message = "Số lượng thuốc phải lớn hơn 0")
        @Max(value = 1000, message = "Số lượng thuốc không được vượt quá 1000")
        Integer quantity,

        @Size(max = 1000, message = "Hướng dẫn sử dụng không được vượt quá 1000 ký tự")
        String instructions
) {
}
