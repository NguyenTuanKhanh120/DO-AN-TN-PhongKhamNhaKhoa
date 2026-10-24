package com.dentalclinic.doctor.controller;

import com.dentalclinic.auth.security.CurrentUser;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.doctor.dto.DoctorAssignedServiceResponse;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.doctor.service.DoctorServiceMappingService;
import com.dentalclinic.service.dto.DentalServiceResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @GetMapping("/api/doctor/services")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(
            summary = "Lấy dịch vụ được phân công cho bác sĩ hiện tại",
            description = "Chỉ trả về dịch vụ đang hoạt động được phân công cho bác sĩ từ JWT."
    )
    public ApiResponse<List<DoctorAssignedServiceResponse>> getMyAssignedServices(
            @CurrentUser UserPrincipal currentUser
    ) {
        return ApiResponse.success(
                "Lấy danh sách dịch vụ của bác sĩ thành công",
                doctorServiceMappingService.getMyActiveServices(currentUser.getId())
        );
    }
}
