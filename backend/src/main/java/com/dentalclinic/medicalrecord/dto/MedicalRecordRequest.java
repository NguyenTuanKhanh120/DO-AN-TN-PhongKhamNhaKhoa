package com.dentalclinic.medicalrecord.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record MedicalRecordRequest(
        @NotBlank(message = "Chẩn đoán không được để trống")
        String diagnosis,

        @NotBlank(message = "Nội dung điều trị không được để trống")
        String treatment,

        String treatmentPlan,

        String notes,

        @Valid
        List<@NotNull(message = "Thuốc trong đơn không được để trống") PrescriptionItem> prescription,

        @NotNull(message = "Lựa chọn tái khám không được để trống")
        Boolean followUpRequired,

        LocalDate recommendedFollowUpDate,

        String followUpNote
) {
}
