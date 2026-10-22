package com.dentalclinic.medicalrecord.controller;

import com.dentalclinic.auth.security.CurrentUser;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.medicalrecord.dto.MedicalRecordRequest;
import com.dentalclinic.medicalrecord.dto.MedicalRecordResponse;
import com.dentalclinic.medicalrecord.service.MedicalRecordService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/doctor/appointments")
@PreAuthorize("hasRole('DOCTOR')")
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    public MedicalRecordController(MedicalRecordService medicalRecordService) {
        this.medicalRecordService = medicalRecordService;
    }

    @PostMapping("/{id}/medical-record")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MedicalRecordResponse> createMedicalRecord(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody MedicalRecordRequest request
    ) {
        return ApiResponse.success(
                "Tạo bệnh án thành công",
                medicalRecordService.create(currentUser.getId(), id, request)
        );
    }

    @PutMapping("/{id}/medical-record")
    public ApiResponse<MedicalRecordResponse> updateMedicalRecord(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody MedicalRecordRequest request
    ) {
        return ApiResponse.success(
                "Cập nhật bệnh án thành công",
                medicalRecordService.update(currentUser.getId(), id, request)
        );
    }

    @GetMapping("/{id}/medical-record")
    public ApiResponse<MedicalRecordResponse> getMedicalRecord(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        return ApiResponse.success(medicalRecordService.get(currentUser.getId(), id));
    }
}
