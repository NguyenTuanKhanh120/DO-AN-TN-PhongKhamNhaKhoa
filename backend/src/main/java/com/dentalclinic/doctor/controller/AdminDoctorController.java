package com.dentalclinic.doctor.controller;

import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.doctor.dto.AdminDoctorResponse;
import com.dentalclinic.doctor.dto.CreateDoctorRequest;
import com.dentalclinic.doctor.dto.UpdateDoctorRequest;
import com.dentalclinic.doctor.dto.UpdateDoctorStatusRequest;
import com.dentalclinic.doctor.service.DoctorManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/doctors")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDoctorController {

    private final DoctorManagementService doctorManagementService;

    public AdminDoctorController(DoctorManagementService doctorManagementService) {
        this.doctorManagementService = doctorManagementService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminDoctorResponse>> createDoctor(
            @Valid @RequestBody CreateDoctorRequest request
    ) {
        AdminDoctorResponse response = doctorManagementService.createDoctor(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo tài khoản bác sĩ thành công", response));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminDoctorResponse> updateDoctor(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDoctorRequest request
    ) {
        return ApiResponse.success(
                "Cập nhật thông tin bác sĩ thành công",
                doctorManagementService.updateDoctor(id, request)
        );
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminDoctorResponse> updateWorkingStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDoctorStatusRequest request
    ) {
        return ApiResponse.success(
                "Cập nhật trạng thái làm việc thành công",
                doctorManagementService.updateWorkingStatus(id, request.status())
        );
    }
}
