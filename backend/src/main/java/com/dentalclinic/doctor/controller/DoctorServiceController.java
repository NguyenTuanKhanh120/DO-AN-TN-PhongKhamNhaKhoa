package com.dentalclinic.doctor.controller;

import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.doctor.service.DoctorServiceMappingService;
import com.dentalclinic.service.dto.DentalServiceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class DoctorServiceController {

    private final DoctorServiceMappingService doctorServiceMappingService;

    public DoctorServiceController(DoctorServiceMappingService doctorServiceMappingService) {
        this.doctorServiceMappingService = doctorServiceMappingService;
    }

    @GetMapping("/api/doctors/{doctorId}/services")
    public ApiResponse<List<DentalServiceResponse>> getServicesByDoctor(@PathVariable UUID doctorId) {
        return ApiResponse.success(doctorServiceMappingService.getServicesByDoctor(doctorId));
    }

    @GetMapping("/api/services/{serviceId}/doctors")
    public ApiResponse<List<DoctorResponse>> getDoctorsByService(@PathVariable UUID serviceId) {
        return ApiResponse.success(doctorServiceMappingService.getDoctorsByService(serviceId));
    }
}
