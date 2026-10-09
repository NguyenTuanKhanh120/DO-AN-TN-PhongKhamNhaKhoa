package com.dentalclinic.schedule.controller;

import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.schedule.dto.AvailableSlotResponse;
import com.dentalclinic.schedule.service.SlotService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class AvailableSlotController {

    private final SlotService slotService;

    public AvailableSlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping("/api/doctors/{doctorId}/available-slots")
    public ApiResponse<List<AvailableSlotResponse>> getAvailableSlots(
            @PathVariable UUID doctorId,
            @RequestParam UUID serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(slotService.getAvailableSlots(doctorId, serviceId, date));
    }
}
