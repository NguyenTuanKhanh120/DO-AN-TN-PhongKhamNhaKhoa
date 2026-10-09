package com.dentalclinic.service.controller;

import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.service.dto.CreateDentalServiceRequest;
import com.dentalclinic.service.dto.DentalServiceResponse;
import com.dentalclinic.service.dto.UpdateDentalServiceRequest;
import com.dentalclinic.service.dto.UpdateDentalServiceStatusRequest;
import com.dentalclinic.service.service.DentalServiceManagementService;
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
@RequestMapping("/api/admin/services")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDentalServiceController {

    private final DentalServiceManagementService dentalServiceManagementService;

    public AdminDentalServiceController(DentalServiceManagementService dentalServiceManagementService) {
        this.dentalServiceManagementService = dentalServiceManagementService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DentalServiceResponse>> createService(
            @Valid @RequestBody CreateDentalServiceRequest request
    ) {
        DentalServiceResponse response = dentalServiceManagementService.createService(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo dịch vụ nha khoa thành công", response));
    }

    @PutMapping("/{id}")
    public ApiResponse<DentalServiceResponse> updateService(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDentalServiceRequest request
    ) {
        return ApiResponse.success(
                "Cập nhật dịch vụ nha khoa thành công",
                dentalServiceManagementService.updateService(id, request)
        );
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<DentalServiceResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDentalServiceStatusRequest request
    ) {
        return ApiResponse.success(
                "Cập nhật trạng thái dịch vụ thành công",
                dentalServiceManagementService.updateStatus(id, request.active())
        );
    }
}
