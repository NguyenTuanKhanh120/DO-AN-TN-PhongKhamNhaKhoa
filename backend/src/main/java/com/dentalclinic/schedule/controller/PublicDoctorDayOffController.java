package com.dentalclinic.schedule.controller;

import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.schedule.dto.PublicDoctorDayOffResponse;
import com.dentalclinic.schedule.service.DoctorDayOffService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
public class PublicDoctorDayOffController {

    private final DoctorDayOffService doctorDayOffService;

    public PublicDoctorDayOffController(DoctorDayOffService doctorDayOffService) {
        this.doctorDayOffService = doctorDayOffService;
    }

    @GetMapping("/api/doctors/{doctorId}/days-off")
    public ApiResponse<List<PublicDoctorDayOffResponse>> getDaysOff(
            @PathVariable UUID doctorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate
    ) {
        return ApiResponse.success(doctorDayOffService.getPublicDaysOff(
                doctorId,
                fromDate,
                toDate
        ));
    }
}
