package com.dentalclinic.appointment.controller;

import com.dentalclinic.appointment.dto.AppointmentResponse;
import com.dentalclinic.appointment.dto.CompleteExaminationResponse;
import com.dentalclinic.appointment.dto.NoShowAppointmentResponse;
import com.dentalclinic.appointment.dto.StartExaminationResponse;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.service.AppointmentCompleteExaminationService;
import com.dentalclinic.appointment.service.AppointmentNoShowService;
import com.dentalclinic.appointment.service.AppointmentQueryService;
import com.dentalclinic.appointment.service.AppointmentStartExaminationService;
import com.dentalclinic.auth.security.CurrentUser;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.response.ApiResponse;
import com.dentalclinic.common.response.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/doctor/appointments")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorAppointmentController {

    private final AppointmentQueryService appointmentQueryService;
    private final AppointmentStartExaminationService appointmentStartExaminationService;
    private final AppointmentNoShowService appointmentNoShowService;
    private final AppointmentCompleteExaminationService appointmentCompleteExaminationService;

    public DoctorAppointmentController(
            AppointmentQueryService appointmentQueryService,
            AppointmentStartExaminationService appointmentStartExaminationService,
            AppointmentNoShowService appointmentNoShowService,
            AppointmentCompleteExaminationService appointmentCompleteExaminationService
    ) {
        this.appointmentQueryService = appointmentQueryService;
        this.appointmentStartExaminationService = appointmentStartExaminationService;
        this.appointmentNoShowService = appointmentNoShowService;
        this.appointmentCompleteExaminationService = appointmentCompleteExaminationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AppointmentResponse>> getAppointments(
            @CurrentUser UserPrincipal currentUser,
            @RequestParam(required = false)
            @Size(max = 255, message = "Từ khóa tìm kiếm không được vượt quá 255 ký tự")
            String search,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) UUID patientId,
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
        return ApiResponse.success(appointmentQueryService.getDoctorAppointments(
                currentUser.getId(),
                search,
                status,
                patientId,
                serviceId,
                fromDate,
                toDate,
                page,
                size
        ));
    }

    @GetMapping("/today")
    public ApiResponse<PageResponse<AppointmentResponse>> getTodayAppointments(
            @CurrentUser UserPrincipal currentUser,
            @RequestParam(required = false)
            @Size(max = 255, message = "Từ khóa tìm kiếm không được vượt quá 255 ký tự")
            String search,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page phải lớn hơn hoặc bằng 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size phải lớn hơn hoặc bằng 1")
            @Max(value = 100, message = "Size không được vượt quá 100")
            int size
    ) {
        return ApiResponse.success(appointmentQueryService.getDoctorTodayAppointments(
                currentUser.getId(),
                search,
                status,
                patientId,
                serviceId,
                page,
                size
        ));
    }

    @PatchMapping("/{id}/start")
    public ApiResponse<StartExaminationResponse> startExamination(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        return ApiResponse.success(
                "Bắt đầu khám thành công",
                appointmentStartExaminationService.start(currentUser.getId(), id)
        );
    }

    @PatchMapping("/{id}/no-show")
    public ApiResponse<NoShowAppointmentResponse> markNoShow(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        return ApiResponse.success(
                "Đánh dấu bệnh nhân không đến khám thành công",
                appointmentNoShowService.markNoShow(currentUser.getId(), id)
        );
    }

    @PatchMapping("/{id}/complete")
    public ApiResponse<CompleteExaminationResponse> completeExamination(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        return ApiResponse.success(
                "Hoàn tất khám thành công",
                appointmentCompleteExaminationService.complete(currentUser.getId(), id)
        );
    }

}
