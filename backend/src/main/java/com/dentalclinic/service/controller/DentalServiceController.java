package com.dentalclinic.service.controller;

import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.service.dto.DentalServiceResponse;
import com.dentalclinic.service.service.DentalServiceManagementService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/services")
public class DentalServiceController {

    private final DentalServiceManagementService dentalServiceManagementService;

    public DentalServiceController(DentalServiceManagementService dentalServiceManagementService) {
        this.dentalServiceManagementService = dentalServiceManagementService;
    }

    @GetMapping
    public ApiResponse<PageResponse<DentalServiceResponse>> getServices(
            @RequestParam(required = false)
            @Size(max = 255, message = "Từ khóa tìm kiếm không được vượt quá 255 ký tự")
            String search,
            @RequestParam(defaultValue = "true") boolean active,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page phải lớn hơn hoặc bằng 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size phải lớn hơn hoặc bằng 1")
            @Max(value = 100, message = "Size không được vượt quá 100")
            int size
    ) {
        return ApiResponse.success(dentalServiceManagementService.getServices(search, active, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<DentalServiceResponse> getService(@PathVariable UUID id) {
        return ApiResponse.success(dentalServiceManagementService.getService(id));
    }
}
