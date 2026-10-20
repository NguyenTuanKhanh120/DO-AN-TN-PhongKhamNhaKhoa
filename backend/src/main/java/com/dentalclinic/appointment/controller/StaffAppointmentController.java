package com.dentalclinic.appointment.controller;

import com.dentalclinic.appointment.dto.AppointmentResponse;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.service.AppointmentQueryService;
import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.common.response.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/staff/appointments")
@PreAuthorize("hasRole('STAFF')")
public class StaffAppointmentController {

    private final AppointmentQueryService appointmentQueryService;

    public StaffAppointmentController(AppointmentQueryService appointmentQueryService) {
        this.appointmentQueryService = appointmentQueryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AppointmentResponse>> getAppointments(
            @RequestParam(required = false)
            @Size(max = 255, message = "Từ khóa tìm kiếm không được vượt quá 255 ký tự")
            String search,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) UUID doctorId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page phải lớn hơn hoặc bằng 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size phải lớn hơn hoặc bằng 1")
            @Max(value = 100, message = "Size không được vượt quá 100")
            int size,
            @RequestParam(defaultValue = "scheduledStartAt") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        return ApiResponse.success(appointmentQueryService.getStaffAppointments(
                search,
                status,
                doctorId,
                serviceId,
                fromDate,
                toDate,
                page,
                size,
                sortBy,
                direction
        ));
    }
}
