package com.dentalclinic.medicalrecord.controller;

import com.dentalclinic.auth.security.CurrentUser;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.medicalrecord.dto.PatientMedicalRecordResponse;
import com.dentalclinic.medicalrecord.service.PatientMedicalHistoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/patient/medical-records")
@PreAuthorize("hasRole('PATIENT')")
public class PatientMedicalRecordController {

    private final PatientMedicalHistoryService patientMedicalHistoryService;

    public PatientMedicalRecordController(
            PatientMedicalHistoryService patientMedicalHistoryService
    ) {
        this.patientMedicalHistoryService = patientMedicalHistoryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PatientMedicalRecordResponse>> getMedicalHistory(
            @CurrentUser UserPrincipal currentUser,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page phải lớn hơn hoặc bằng 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size phải lớn hơn hoặc bằng 1")
            @Max(value = 100, message = "Size không được vượt quá 100")
            int size
    ) {
        return ApiResponse.success(patientMedicalHistoryService.getMedicalHistory(
                currentUser.getId(),
                page,
                size
        ));
    }

    @GetMapping("/{appointmentId}")
    public ApiResponse<PatientMedicalRecordResponse> getMedicalRecord(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID appointmentId
    ) {
        return ApiResponse.success(patientMedicalHistoryService.getMedicalRecord(
                currentUser.getId(),
                appointmentId
        ));
    }
}
