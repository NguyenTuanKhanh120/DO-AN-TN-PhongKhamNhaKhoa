package com.dentalclinic.schedule.controller;

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
@RequestMapping("/api/admin/doctors/{doctorId}/schedules")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDoctorScheduleController {

    private final DoctorScheduleService doctorScheduleService;

    public AdminDoctorScheduleController(DoctorScheduleService doctorScheduleService) {
        this.doctorScheduleService = doctorScheduleService;
    }

    @GetMapping
    public ApiResponse<List<DoctorScheduleResponse>> getSchedules(
            @PathVariable UUID doctorId
    ) {
        return ApiResponse.success(doctorScheduleService.getAdminSchedules(doctorId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DoctorScheduleResponse>> createSchedule(
            @PathVariable UUID doctorId,
            @Valid @RequestBody DoctorScheduleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        "Tạo lịch làm việc thành công",
                        doctorScheduleService.createSchedule(doctorId, request)
                )
        );
    }

    @PutMapping("/{scheduleId}")
    public ApiResponse<DoctorScheduleResponse> updateSchedule(
            @PathVariable UUID doctorId,
            @PathVariable UUID scheduleId,
            @Valid @RequestBody DoctorScheduleRequest request
    ) {
        return ApiResponse.success(
                "Cập nhật lịch làm việc thành công",
                doctorScheduleService.updateSchedule(doctorId, scheduleId, request)
        );
    }

    @DeleteMapping("/{scheduleId}")
    public ApiResponse<Void> deleteSchedule(
            @PathVariable UUID doctorId,
            @PathVariable UUID scheduleId
    ) {
        doctorScheduleService.deleteSchedule(doctorId, scheduleId);
        return ApiResponse.success("Xóa lịch làm việc thành công", null);
    }
}
