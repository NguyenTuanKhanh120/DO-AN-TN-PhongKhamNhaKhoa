package com.dentalclinic.doctor.controller;

import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.service.DoctorManagementService;
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
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorManagementService doctorManagementService;

    public DoctorController(DoctorManagementService doctorManagementService) {
        this.doctorManagementService = doctorManagementService;
    }

    @GetMapping
    public ApiResponse<PageResponse<DoctorResponse>> getDoctors(
            @RequestParam(required = false)
            @Size(max = 255, message = "Từ khóa tìm kiếm không được vượt quá 255 ký tự")
            String search,
            @RequestParam(required = false)
            @Size(max = 150, message = "Chuyên khoa không được vượt quá 150 ký tự")
            String specialization,
            @RequestParam(required = false) DoctorStatus workingStatus,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page phải lớn hơn hoặc bằng 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size phải lớn hơn hoặc bằng 1")
            @Max(value = 100, message = "Size không được vượt quá 100")
            int size
    ) {
        return ApiResponse.success(
                doctorManagementService.getDoctors(search, specialization, workingStatus, page, size)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<DoctorResponse> getDoctor(@PathVariable UUID id) {
        return ApiResponse.success(doctorManagementService.getDoctor(id));
    }
}
