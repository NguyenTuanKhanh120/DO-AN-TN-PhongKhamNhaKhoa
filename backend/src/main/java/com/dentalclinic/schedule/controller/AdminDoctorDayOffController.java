package com.dentalclinic.schedule.controller;

import com.dentalclinic.auth.security.CurrentUser;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.schedule.dto.AdminDoctorDayOffResponse;
import com.dentalclinic.schedule.dto.CreateDoctorDayOffRequest;
import com.dentalclinic.schedule.dto.CreateDoctorDayOffResponse;
import com.dentalclinic.schedule.dto.DoctorDayOffPreviewResponse;
import com.dentalclinic.schedule.service.DoctorDayOffService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/admin/doctors/{doctorId}/days-off")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDoctorDayOffController {

    private final DoctorDayOffService doctorDayOffService;

    public AdminDoctorDayOffController(DoctorDayOffService doctorDayOffService) {
        this.doctorDayOffService = doctorDayOffService;
    }

    @PostMapping("/preview")
    public ApiResponse<DoctorDayOffPreviewResponse> preview(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID doctorId,
            @Valid @RequestBody CreateDoctorDayOffRequest request
    ) {
        return ApiResponse.success(doctorDayOffService.preview(
                currentUser.getId(),
                doctorId,
                request
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateDoctorDayOffResponse>> create(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID doctorId,
            @Valid @RequestBody CreateDoctorDayOffRequest request
    ) {
        CreateDoctorDayOffResponse response = doctorDayOffService.create(
                currentUser.getId(),
                doctorId,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký ngày nghỉ bác sĩ thành công", response));
    }

    @GetMapping
    public ApiResponse<List<AdminDoctorDayOffResponse>> getDaysOff(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID doctorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate
    ) {
        return ApiResponse.success(doctorDayOffService.getAdminDaysOff(
                currentUser.getId(),
                doctorId,
                fromDate,
                toDate
        ));
    }

    @DeleteMapping("/{dayOffId}")
    public ApiResponse<Void> delete(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID doctorId,
            @PathVariable UUID dayOffId
    ) {
        doctorDayOffService.delete(currentUser.getId(), doctorId, dayOffId);
        return ApiResponse.success("Xóa ngày nghỉ bác sĩ thành công", null);
    }
}
