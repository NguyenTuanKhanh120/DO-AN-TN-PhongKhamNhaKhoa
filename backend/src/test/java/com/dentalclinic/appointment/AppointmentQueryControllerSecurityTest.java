package com.dentalclinic.appointment;

import com.dentalclinic.appointment.controller.AdminAppointmentController;
import com.dentalclinic.appointment.controller.DoctorAppointmentController;
import com.dentalclinic.appointment.dto.AppointmentResponse;
import com.dentalclinic.appointment.dto.CheckInAppointmentResponse;
import com.dentalclinic.appointment.dto.CompleteExaminationResponse;
import com.dentalclinic.appointment.dto.CreateFollowUpAppointmentRequest;
import com.dentalclinic.appointment.dto.FollowUpAppointmentResponse;
import com.dentalclinic.appointment.dto.NoShowAppointmentResponse;
import com.dentalclinic.appointment.dto.StartExaminationResponse;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.service.AppointmentCheckInService;
import com.dentalclinic.appointment.service.AppointmentCompleteExaminationService;
import com.dentalclinic.appointment.service.AppointmentHistoryService;
import com.dentalclinic.appointment.service.AppointmentNoShowService;
import com.dentalclinic.appointment.service.AppointmentQueryService;
import com.dentalclinic.appointment.service.AppointmentStartExaminationService;
import com.dentalclinic.appointment.service.FollowUpAppointmentService;
import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.schedule.dto.AvailableSlotResponse;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({DoctorAppointmentController.class, AdminAppointmentController.class})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class AppointmentQueryControllerSecurityTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppointmentQueryService appointmentQueryService;

    @MockitoBean
    private AppointmentCheckInService appointmentCheckInService;

    @MockitoBean
    private AppointmentStartExaminationService appointmentStartExaminationService;

    @MockitoBean
    private AppointmentNoShowService appointmentNoShowService;

    @MockitoBean
    private AppointmentCompleteExaminationService appointmentCompleteExaminationService;

    @MockitoBean
    private FollowUpAppointmentService followUpAppointmentService;

    @MockitoBean
    private AppointmentHistoryService appointmentHistoryService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "appointment-query-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldRequireAuthenticationForDoctorAppointments() throws Exception {
        mockMvc.perform(get("/api/doctor/appointments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldRejectPatientFromDoctorAppointments() throws Exception {
        mockMvc.perform(get("/api/doctor/appointments")
                        .with(user("patient@example.com").roles("PATIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldReturnAuthenticatedDoctorsAppointments() throws Exception {
        UserPrincipal principal = principal(Role.DOCTOR);
        AppointmentResponse appointment = appointmentResponse();
        when(appointmentQueryService.getDoctorAppointments(
                eq(principal.getId()),
                eq("bệnh nhân"),
                eq(AppointmentStatus.BOOKED),
                any(),
                any(),
                any(),
                any(),
                eq(0),
                eq(20)
        )).thenReturn(page(appointment));

        mockMvc.perform(get("/api/doctor/appointments")
                        .with(user(principal))
                        .param("search", "bệnh nhân")
                        .param("status", "BOOKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id")
                        .value(appointment.id().toString()));
    }

    @Test
    void shouldReturnDoctorsTodayAppointments() throws Exception {
        UserPrincipal principal = principal(Role.DOCTOR);
        AppointmentResponse appointment = appointmentResponse();
        when(appointmentQueryService.getDoctorTodayAppointments(
                principal.getId(),
                null,
                null,
                null,
                null,
                0,
                20
        )).thenReturn(page(appointment));

        mockMvc.perform(get("/api/doctor/appointments/today")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].scheduledStartAt")
                        .value("2026-10-12T09:00:00+07:00"));

        verify(appointmentQueryService).getDoctorTodayAppointments(
                principal.getId(),
                null,
                null,
                null,
                null,
                0,
                20
        );
    }

    @Test
    void shouldRejectDoctorFromAdminAppointments() throws Exception {
        mockMvc.perform(get("/api/admin/appointments")
                        .with(user("doctor@example.com").roles("DOCTOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowAdminToFilterAppointments() throws Exception {
        UUID doctorId = UUID.randomUUID();
        AppointmentResponse appointment = appointmentResponse();
        when(appointmentQueryService.getAdminAppointments(
                eq("trám răng"),
                eq(AppointmentStatus.BOOKED),
                any(),
                eq(doctorId),
                any(),
                any(),
                any(),
                eq(0),
                eq(20)
        )).thenReturn(page(appointment));

        mockMvc.perform(get("/api/admin/appointments")
                        .with(user(principal(Role.ADMIN)))
                        .param("search", "trám răng")
                        .param("status", "BOOKED")
                        .param("doctorId", doctorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    void shouldValidateAdminAppointmentQueryParameters() throws Exception {
        mockMvc.perform(get("/api/admin/appointments")
                        .with(user(principal(Role.ADMIN)))
                        .param("fromDate", "12-10-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldAllowAssignedDoctorToCheckInAppointment() throws Exception {
        UserPrincipal principal = principal(Role.DOCTOR);
        UUID appointmentId = UUID.randomUUID();
        CheckInAppointmentResponse response = new CheckInAppointmentResponse(
                appointmentId,
                AppointmentStatus.CHECKED_IN,
                OffsetDateTime.parse("2026-10-12T09:00:00+07:00"),
                OffsetDateTime.parse("2026-10-12T08:55:00+07:00")
        );
        when(appointmentCheckInService.checkIn(principal.getId(), appointmentId))
                .thenReturn(response);

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/check-in",
                        appointmentId
                ).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Check-in bệnh nhân thành công"))
                .andExpect(jsonPath("$.data.id").value(appointmentId.toString()))
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.data.scheduledStartAt")
                        .value("2026-10-12T09:00:00+07:00"))
                .andExpect(jsonPath("$.data.checkedInAt")
                        .value("2026-10-12T08:55:00+07:00"));

        verify(appointmentCheckInService).checkIn(principal.getId(), appointmentId);
    }

    @Test
    void shouldRejectPatientFromDoctorCheckIn() throws Exception {
        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/check-in",
                        UUID.randomUUID()
                ).with(user("patient@example.com").roles("PATIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowAssignedDoctorToStartExamination() throws Exception {
        UserPrincipal principal = principal(Role.DOCTOR);
        UUID appointmentId = UUID.randomUUID();
        StartExaminationResponse response = new StartExaminationResponse(
                appointmentId,
                AppointmentStatus.IN_PROGRESS,
                OffsetDateTime.parse("2026-10-12T09:00:00+07:00"),
                OffsetDateTime.parse("2026-10-12T08:55:00+07:00"),
                OffsetDateTime.parse("2026-10-12T09:15:00+07:00")
        );
        when(appointmentStartExaminationService.start(principal.getId(), appointmentId))
                .thenReturn(response);

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/start",
                        appointmentId
                ).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Bắt đầu khám thành công"))
                .andExpect(jsonPath("$.data.id").value(appointmentId.toString()))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.scheduledStartAt")
                        .value("2026-10-12T09:00:00+07:00"))
                .andExpect(jsonPath("$.data.checkedInAt")
                        .value("2026-10-12T08:55:00+07:00"))
                .andExpect(jsonPath("$.data.actualStartAt")
                        .value("2026-10-12T09:15:00+07:00"));

        verify(appointmentStartExaminationService).start(principal.getId(), appointmentId);
    }

    @Test
    void shouldRejectPatientFromDoctorStartExamination() throws Exception {
        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/start",
                        UUID.randomUUID()
                ).with(user("patient@example.com").roles("PATIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowAssignedDoctorToMarkNoShow() throws Exception {
        UserPrincipal principal = principal(Role.DOCTOR);
        UUID appointmentId = UUID.randomUUID();
        NoShowAppointmentResponse response = new NoShowAppointmentResponse(
                appointmentId,
                AppointmentStatus.NO_SHOW,
                OffsetDateTime.parse("2026-10-12T09:00:00+07:00"),
                OffsetDateTime.parse("2026-10-12T09:15:00+07:00")
        );
        when(appointmentNoShowService.markNoShow(principal.getId(), appointmentId))
                .thenReturn(response);

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/no-show",
                        appointmentId
                ).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Đánh dấu bệnh nhân không đến khám thành công"))
                .andExpect(jsonPath("$.data.id").value(appointmentId.toString()))
                .andExpect(jsonPath("$.data.status").value("NO_SHOW"))
                .andExpect(jsonPath("$.data.scheduledStartAt")
                        .value("2026-10-12T09:00:00+07:00"))
                .andExpect(jsonPath("$.data.noShowAt")
                        .value("2026-10-12T09:15:00+07:00"));

        verify(appointmentNoShowService).markNoShow(principal.getId(), appointmentId);
    }

    @Test
    void shouldRejectPatientFromDoctorNoShow() throws Exception {
        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/no-show",
                        UUID.randomUUID()
                ).with(user("patient@example.com").roles("PATIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowAssignedDoctorToCompleteExamination() throws Exception {
        UserPrincipal principal = principal(Role.DOCTOR);
        UUID appointmentId = UUID.randomUUID();
        CompleteExaminationResponse response = new CompleteExaminationResponse(
                appointmentId,
                AppointmentStatus.COMPLETED,
                OffsetDateTime.parse("2026-10-12T09:00:00+07:00"),
                OffsetDateTime.parse("2026-10-12T08:55:00+07:00"),
                OffsetDateTime.parse("2026-10-12T09:15:00+07:00"),
                OffsetDateTime.parse("2026-10-12T09:45:00+07:00")
        );
        when(appointmentCompleteExaminationService.complete(
                principal.getId(),
                appointmentId
        )).thenReturn(response);

        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/complete",
                        appointmentId
                ).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hoàn tất khám thành công"))
                .andExpect(jsonPath("$.data.id").value(appointmentId.toString()))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.actualStartAt")
                        .value("2026-10-12T09:15:00+07:00"))
                .andExpect(jsonPath("$.data.actualEndAt")
                        .value("2026-10-12T09:45:00+07:00"));

        verify(appointmentCompleteExaminationService).complete(
                principal.getId(),
                appointmentId
        );
    }

    @Test
    void shouldRejectPatientFromDoctorCompleteExamination() throws Exception {
        mockMvc.perform(patch(
                        "/api/doctor/appointments/{id}/complete",
                        UUID.randomUUID()
                ).with(user("patient@example.com").roles("PATIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowAssignedDoctorToGetFollowUpSlots() throws Exception {
        UserPrincipal principal = principal(Role.DOCTOR);
        UUID appointmentId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 20);
        AvailableSlotResponse slot = new AvailableSlotResponse(
                date,
                ScheduleSession.MORNING,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                OffsetDateTime.parse("2026-10-20T09:00:00+07:00"),
                OffsetDateTime.parse("2026-10-20T10:00:00+07:00")
        );
        when(followUpAppointmentService.getFollowUpSlots(
                principal.getId(),
                appointmentId,
                serviceId,
                date
        )).thenReturn(List.of(slot));

        mockMvc.perform(get(
                        "/api/doctor/appointments/{id}/follow-up-slots",
                        appointmentId
                ).with(user(principal))
                        .param("serviceId", serviceId.toString())
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].startAt")
                        .value("2026-10-20T09:00:00+07:00"));

        verify(followUpAppointmentService).getFollowUpSlots(
                principal.getId(),
                appointmentId,
                serviceId,
                date
        );
    }

    @Test
    void shouldAllowAssignedDoctorToCreateFollowUpAppointment() throws Exception {
        UserPrincipal principal = principal(Role.DOCTOR);
        UUID previousAppointmentId = UUID.randomUUID();
        UUID followUpAppointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        CreateFollowUpAppointmentRequest request = new CreateFollowUpAppointmentRequest(
                serviceId,
                OffsetDateTime.parse("2026-10-20T09:00:00+07:00")
        );
        FollowUpAppointmentResponse response = new FollowUpAppointmentResponse(
                followUpAppointmentId,
                previousAppointmentId,
                patientId,
                doctorId,
                serviceId,
                request.scheduledStartAt(),
                request.scheduledStartAt().plusHours(1),
                AppointmentStatus.BOOKED,
                Instant.parse("2026-10-12T03:00:00Z")
        );
        when(followUpAppointmentService.createFollowUp(
                principal.getId(),
                previousAppointmentId,
                request
        )).thenReturn(response);

        mockMvc.perform(post(
                        "/api/doctor/appointments/{id}/follow-up",
                        previousAppointmentId
                ).with(user(principal))
                        .contentType("application/json")
                        .content("""
                                {
                                  "serviceId": "%s",
                                  "scheduledStartAt": "2026-10-20T09:00:00+07:00"
                                }
                                """.formatted(serviceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đặt lịch tái khám thành công"))
                .andExpect(jsonPath("$.data.id").value(followUpAppointmentId.toString()))
                .andExpect(jsonPath("$.data.previousAppointmentId")
                        .value(previousAppointmentId.toString()))
                .andExpect(jsonPath("$.data.status").value("BOOKED"));

        verify(followUpAppointmentService).createFollowUp(
                principal.getId(),
                previousAppointmentId,
                request
        );
    }

    @Test
    void shouldValidateCreateFollowUpRequest() throws Exception {
        mockMvc.perform(post(
                        "/api/doctor/appointments/{id}/follow-up",
                        UUID.randomUUID()
                ).with(user(principal(Role.DOCTOR)))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void shouldRejectPatientFromDoctorFollowUpApis() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        mockMvc.perform(get(
                        "/api/doctor/appointments/{id}/follow-up-slots",
                        appointmentId
                ).with(user("patient@example.com").roles("PATIENT"))
                        .param("serviceId", UUID.randomUUID().toString())
                        .param("date", "2026-10-20"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post(
                        "/api/doctor/appointments/{id}/follow-up",
                        appointmentId
                ).with(user("patient@example.com").roles("PATIENT"))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private PageResponse<AppointmentResponse> page(AppointmentResponse appointment) {
        return new PageResponse<>(
                List.of(appointment),
                0,
                20,
                1,
                1,
                true,
                true,
                false,
                false
        );
    }

    private AppointmentResponse appointmentResponse() {
        return new AppointmentResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Bệnh nhân Nguyễn Văn Bình",
                "0901000001",
                UUID.randomUUID(),
                "Bác sĩ Nguyễn Minh Anh",
                "Nha khoa tổng quát",
                UUID.randomUUID(),
                "Trám răng",
                null,
                OffsetDateTime.parse("2026-10-12T09:00:00+07:00"),
                OffsetDateTime.parse("2026-10-12T10:00:00+07:00"),
                AppointmentStatus.BOOKED,
                null,
                null,
                null,
                Instant.parse("2026-10-10T03:00:00Z"),
                Instant.parse("2026-10-10T03:00:00Z")
        );
    }

    private UserPrincipal principal(Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(role.name().toLowerCase() + "@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setFullName("Người dùng kiểm thử");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return UserPrincipal.from(user);
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
