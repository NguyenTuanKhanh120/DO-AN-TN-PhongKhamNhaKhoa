package com.dentalclinic.patient.controller;

import com.dentalclinic.auth.security.CurrentUser;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.patient.dto.PatientProfileResponse;
import com.dentalclinic.patient.dto.UpdatePatientProfileRequest;
import com.dentalclinic.patient.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
@PreAuthorize("hasRole('PATIENT')")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping("/me")
    public ApiResponse<PatientProfileResponse> getMyProfile(
            @CurrentUser UserPrincipal currentUser
    ) {
        return ApiResponse.success(patientService.getMyProfile(currentUser.getId()));
    }

    @PutMapping("/me")
    public ApiResponse<PatientProfileResponse> updateMyProfile(
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody UpdatePatientProfileRequest request
    ) {
        PatientProfileResponse response = patientService.updateMyProfile(currentUser.getId(), request);
        return ApiResponse.success("Cập nhật hồ sơ thành công", response);
    }
}
