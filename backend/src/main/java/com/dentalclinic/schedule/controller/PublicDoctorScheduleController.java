package com.dentalclinic.schedule.controller;

import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.schedule.dto.DoctorScheduleResponse;
import com.dentalclinic.schedule.service.DoctorScheduleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class PublicDoctorScheduleController {

    private final DoctorScheduleService doctorScheduleService;

    public PublicDoctorScheduleController(DoctorScheduleService doctorScheduleService) {
        this.doctorScheduleService = doctorScheduleService;
    }

    @GetMapping("/api/doctors/{doctorId}/schedule")
    public ApiResponse<List<DoctorScheduleResponse>> getDoctorSchedule(@PathVariable UUID doctorId) {
        return ApiResponse.success(doctorScheduleService.getPublicSchedule(doctorId));
    }
}
