package com.dentalclinic.doctor.controller;

import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.doctor.dto.UpdateDoctorServicesRequest;
import com.dentalclinic.doctor.service.DoctorServiceMappingService;
import com.dentalclinic.service.dto.DentalServiceResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/doctors/{doctorId}/services")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDoctorServiceController {

    private final DoctorServiceMappingService doctorServiceMappingService;

    public AdminDoctorServiceController(DoctorServiceMappingService doctorServiceMappingService) {
        this.doctorServiceMappingService = doctorServiceMappingService;
    }

    @PutMapping
    public ApiResponse<List<DentalServiceResponse>> replaceDoctorServices(
            @PathVariable UUID doctorId,
            @Valid @RequestBody UpdateDoctorServicesRequest request
    ) {
        return ApiResponse.success(
                "Cập nhật dịch vụ của bác sĩ thành công",
                doctorServiceMappingService.replaceDoctorServices(doctorId, request.serviceIds())
        );
    }
}
