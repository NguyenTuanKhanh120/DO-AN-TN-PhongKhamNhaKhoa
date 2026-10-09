package com.dentalclinic.schedule.controller;

import com.dentalclinic.auth.security.CurrentUser;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.schedule.dto.DoctorScheduleRequest;
import com.dentalclinic.schedule.dto.DoctorScheduleResponse;
import com.dentalclinic.schedule.service.DoctorScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/doctor/schedules")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorScheduleController {

    private final DoctorScheduleService doctorScheduleService;

    public DoctorScheduleController(DoctorScheduleService doctorScheduleService) {
        this.doctorScheduleService = doctorScheduleService;
    }

    @GetMapping
    public ApiResponse<List<DoctorScheduleResponse>> getMySchedules(
            @CurrentUser UserPrincipal currentUser
    ) {
        return ApiResponse.success(doctorScheduleService.getMySchedules(currentUser.getId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DoctorScheduleResponse>> createSchedule(
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody DoctorScheduleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        "Tạo lịch làm việc thành công",
                        doctorScheduleService.createSchedule(currentUser.getId(), request)
                )
        );
    }

    @PutMapping("/{id}")
    public ApiResponse<DoctorScheduleResponse> updateSchedule(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody DoctorScheduleRequest request
    ) {
        return ApiResponse.success(
                "Cập nhật lịch làm việc thành công",
                doctorScheduleService.updateSchedule(currentUser.getId(), id, request)
        );
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSchedule(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        doctorScheduleService.deleteSchedule(currentUser.getId(), id);
        return ApiResponse.success("Xóa lịch làm việc thành công", null);
    }
}
