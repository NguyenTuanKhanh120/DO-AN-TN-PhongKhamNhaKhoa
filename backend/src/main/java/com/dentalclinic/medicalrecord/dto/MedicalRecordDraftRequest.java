package com.dentalclinic.medicalrecord.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record MedicalRecordDraftRequest(
        @Size(max = 5000, message = "Chẩn đoán không được vượt quá 5000 ký tự")
        String diagnosis,

        @Size(max = 10000, message = "Nội dung điều trị không được vượt quá 10000 ký tự")
        String treatment,

        @Size(max = 10000, message = "Kế hoạch điều trị không được vượt quá 10000 ký tự")
        String treatmentPlan,

        @Size(max = 10000, message = "Ghi chú không được vượt quá 10000 ký tự")
        String notes,

        @Valid
        @Size(max = 100, message = "Đơn thuốc không được vượt quá 100 mục")
        List<@NotNull(message = "Thuốc trong bản nháp không được để trống")
                MedicalRecordDraftPrescriptionItem> prescription,

        Boolean followUpRequired,

        LocalDate recommendedFollowUpDate,

        @Size(max = 5000, message = "Ghi chú tái khám không được vượt quá 5000 ký tự")
        String followUpNote
) {
}
