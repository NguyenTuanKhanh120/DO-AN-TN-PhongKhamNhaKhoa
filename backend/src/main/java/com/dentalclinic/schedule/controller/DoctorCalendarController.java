package com.dentalclinic.schedule.controller;

import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.schedule.dto.DoctorCalendarResponse;
import com.dentalclinic.schedule.service.SlotService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
public class DoctorCalendarController {

    private final SlotService slotService;

    public DoctorCalendarController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping("/api/doctors/{doctorId}/calendar")
    public ApiResponse<DoctorCalendarResponse> getCalendar(
            @PathVariable UUID doctorId,
            @RequestParam UUID serviceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return ApiResponse.success(slotService.getCalendar(
                doctorId,
                serviceId,
                fromDate,
                toDate
        ));
    }
}
