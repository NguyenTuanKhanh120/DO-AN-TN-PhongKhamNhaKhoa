package com.dentalclinic.appointment.controller;

import com.dentalclinic.appointment.dto.BookAppointmentRequest;
import com.dentalclinic.appointment.dto.BookAppointmentResponse;
import com.dentalclinic.appointment.dto.AppointmentResponse;
import com.dentalclinic.appointment.dto.CancelAppointmentRequest;
import com.dentalclinic.appointment.dto.CancelAppointmentResponse;
import com.dentalclinic.appointment.dto.RescheduleAppointmentRequest;
import com.dentalclinic.appointment.dto.RescheduleAppointmentResponse;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.service.AppointmentBookingService;
import com.dentalclinic.appointment.service.AppointmentCancellationService;
import com.dentalclinic.appointment.service.AppointmentQueryService;
import com.dentalclinic.appointment.service.AppointmentRescheduleService;
import com.dentalclinic.auth.security.CurrentUser;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.common.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/patient/appointments")
@PreAuthorize("hasRole('PATIENT')")
public class PatientAppointmentController {

    private final AppointmentBookingService appointmentBookingService;
    private final AppointmentQueryService appointmentQueryService;
    private final AppointmentCancellationService appointmentCancellationService;
    private final AppointmentRescheduleService appointmentRescheduleService;

    public PatientAppointmentController(
            AppointmentBookingService appointmentBookingService,
            AppointmentQueryService appointmentQueryService,
            AppointmentCancellationService appointmentCancellationService,
            AppointmentRescheduleService appointmentRescheduleService
    ) {
        this.appointmentBookingService = appointmentBookingService;
        this.appointmentQueryService = appointmentQueryService;
        this.appointmentCancellationService = appointmentCancellationService;
        this.appointmentRescheduleService = appointmentRescheduleService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AppointmentResponse>> getAppointments(
            @CurrentUser UserPrincipal currentUser,
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
            int size
    ) {
        return ApiResponse.success(appointmentQueryService.getPatientAppointments(
                currentUser.getId(),
                search,
                status,
                doctorId,
                serviceId,
                fromDate,
                toDate,
                page,
                size
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<AppointmentResponse> getAppointment(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        return ApiResponse.success(
                appointmentQueryService.getPatientAppointment(currentUser.getId(), id)
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BookAppointmentResponse> bookAppointment(
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody BookAppointmentRequest request
    ) {
        return ApiResponse.success(appointmentBookingService.book(currentUser.getId(), request));
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<CancelAppointmentResponse> cancelAppointment(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody CancelAppointmentRequest request
    ) {
        return ApiResponse.success(
                "Hủy lịch hẹn thành công",
                appointmentCancellationService.cancel(currentUser.getId(), id, request)
        );
    }

    @PatchMapping("/{id}/reschedule")
    public ApiResponse<RescheduleAppointmentResponse> rescheduleAppointment(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleAppointmentRequest request
    ) {
        return ApiResponse.success(
                "Đổi lịch hẹn thành công",
                appointmentRescheduleService.reschedule(currentUser.getId(), id, request)
        );
    }
}
