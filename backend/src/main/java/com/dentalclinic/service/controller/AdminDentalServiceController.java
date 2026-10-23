package com.dentalclinic.service.controller;

import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.service.dto.CreateDentalServiceRequest;
import com.dentalclinic.service.dto.DentalServiceResponse;
import com.dentalclinic.service.dto.DentalServiceImageUploadResponse;
import com.dentalclinic.service.dto.UpdateDentalServiceRequest;
import com.dentalclinic.service.dto.UpdateDentalServiceStatusRequest;
import com.dentalclinic.service.service.DentalServiceManagementService;
import com.dentalclinic.service.service.DentalServiceImageStorageService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/services")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDentalServiceController {

    private final DentalServiceManagementService dentalServiceManagementService;
    private final DentalServiceImageStorageService dentalServiceImageStorageService;

    public AdminDentalServiceController(
            DentalServiceManagementService dentalServiceManagementService,
            DentalServiceImageStorageService dentalServiceImageStorageService
    ) {
        this.dentalServiceManagementService = dentalServiceManagementService;
        this.dentalServiceImageStorageService = dentalServiceImageStorageService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DentalServiceResponse>> createService(
            @Valid @RequestBody CreateDentalServiceRequest request
    ) {
        DentalServiceResponse response = dentalServiceManagementService.createService(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo dịch vụ nha khoa thành công", response));
    }

    @PostMapping(value = "/images", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DentalServiceImageUploadResponse>> uploadServiceImage(
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request
    ) {
        String filename = dentalServiceImageStorageService.store(file);
        String imageUrl = ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath("/api/services/images/")
                .replaceQuery(null)
                .path(filename)
                .build()
                .toUriString();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Tải ảnh dịch vụ thành công",
                        new DentalServiceImageUploadResponse(imageUrl)
                ));
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
