package com.dentalclinic.appointment;

import com.dentalclinic.appointment.controller.PatientAppointmentController;
import com.dentalclinic.appointment.dto.AppointmentResponse;
import com.dentalclinic.appointment.dto.BookAppointmentRequest;
import com.dentalclinic.appointment.dto.BookAppointmentResponse;
import com.dentalclinic.appointment.dto.CancelAppointmentRequest;
import com.dentalclinic.appointment.dto.CancelAppointmentResponse;
import com.dentalclinic.appointment.dto.RescheduleAppointmentRequest;
import com.dentalclinic.appointment.dto.RescheduleAppointmentResponse;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.service.AppointmentBookingService;
import com.dentalclinic.appointment.service.AppointmentCancellationService;
import com.dentalclinic.appointment.service.AppointmentQueryService;
import com.dentalclinic.appointment.service.AppointmentRescheduleService;
import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.time.Instant;
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

@WebMvcTest(PatientAppointmentController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class PatientAppointmentControllerSecurityTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppointmentBookingService appointmentBookingService;

    @MockitoBean
    private AppointmentQueryService appointmentQueryService;

    @MockitoBean
    private AppointmentCancellationService appointmentCancellationService;

    @MockitoBean
    private AppointmentRescheduleService appointmentRescheduleService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "appointment-booking-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/patient/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldRejectDoctorRole() throws Exception {
        mockMvc.perform(post("/api/patient/appointments")
                        .with(user("doctor@example.com").roles("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldBookForAuthenticatedPatient() throws Exception {
        UserPrincipal principal = patientPrincipal();
        UUID doctorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        BookAppointmentResponse response = response(doctorId, serviceId);
        when(appointmentBookingService.book(
                eq(principal.getId()),
                any(BookAppointmentRequest.class)
        )).thenReturn(response);

        mockMvc.perform(post("/api/patient/appointments")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(doctorId, serviceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(response.id().toString()))
                .andExpect(jsonPath("$.data.status").value("BOOKED"))
                .andExpect(jsonPath("$.data.scheduledStartAt")
                        .value("2026-10-12T13:00:00+07:00"))
                .andExpect(jsonPath("$.data.scheduledEndAt")
                        .value("2026-10-12T14:00:00+07:00"));

        verify(appointmentBookingService).book(
                eq(principal.getId()),
                any(BookAppointmentRequest.class)
        );
    }

    @Test
    void shouldValidateRequiredBookingFields() throws Exception {
        mockMvc.perform(post("/api/patient/appointments")
                        .with(user(patientPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void shouldRejectMalformedScheduledStartAt() throws Exception {
        mockMvc.perform(post("/api/patient/appointments")
                        .with(user(patientPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "doctorId": "%s",
                                  "serviceId": "%s",
                                  "scheduledStartAt": "2026-10-12 13:00"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void shouldReturnOnlyAuthenticatedPatientAppointments() throws Exception {
        UserPrincipal principal = patientPrincipal();
        AppointmentResponse appointment = appointmentResponse();
        PageResponse<AppointmentResponse> response = new PageResponse<>(
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
        when(appointmentQueryService.getPatientAppointments(
                eq(principal.getId()),
                eq("trám răng"),
                eq(AppointmentStatus.BOOKED),
                any(),
                any(),
                any(),
                any(),
                eq(0),
                eq(20)
        )).thenReturn(response);

        mockMvc.perform(get("/api/patient/appointments")
                        .with(user(principal))
                        .param("search", "trám răng")
                        .param("status", "BOOKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id")
                        .value(appointment.id().toString()))
                .andExpect(jsonPath("$.data.content[0].patientPhone")
                        .value("0901000001"));
    }

    @Test
    void shouldReturnOwnedPatientAppointmentDetail() throws Exception {
        UserPrincipal principal = patientPrincipal();
        AppointmentResponse appointment = appointmentResponse();
        when(appointmentQueryService.getPatientAppointment(
                principal.getId(),
                appointment.id()
        )).thenReturn(appointment);

        mockMvc.perform(get("/api/patient/appointments/{id}", appointment.id())
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(appointment.id().toString()))
                .andExpect(jsonPath("$.data.status").value("BOOKED"));
    }

    @Test
    void shouldValidatePatientAppointmentPagination() throws Exception {
        mockMvc.perform(get("/api/patient/appointments")
                        .with(user(patientPrincipal()))
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldCancelOwnedBookedAppointment() throws Exception {
        UserPrincipal principal = patientPrincipal();
        UUID appointmentId = UUID.randomUUID();
        CancelAppointmentResponse response = new CancelAppointmentResponse(
                appointmentId,
                AppointmentStatus.CANCELLED,
                OffsetDateTime.parse("2026-10-12T13:00:00+07:00"),
                "Bận việc gia đình",
                OffsetDateTime.parse("2026-10-12T10:00:00+07:00")
        );
        when(appointmentCancellationService.cancel(
                eq(principal.getId()),
                eq(appointmentId),
                any(CancelAppointmentRequest.class)
        )).thenReturn(response);

        mockMvc.perform(patch("/api/patient/appointments/{id}/cancel", appointmentId)
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Bận việc gia đình\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hủy lịch hẹn thành công"))
                .andExpect(jsonPath("$.data.id").value(appointmentId.toString()))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelledAt")
                        .value("2026-10-12T10:00:00+07:00"));

        verify(appointmentCancellationService).cancel(
                eq(principal.getId()),
                eq(appointmentId),
                any(CancelAppointmentRequest.class)
        );
    }

    @Test
    void shouldValidateCancellationReason() throws Exception {
        mockMvc.perform(patch(
                        "/api/patient/appointments/{id}/cancel",
                        UUID.randomUUID()
                ).with(user(patientPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
    }

    @Test
    void shouldRejectDoctorFromPatientCancellation() throws Exception {
        mockMvc.perform(patch(
                        "/api/patient/appointments/{id}/cancel",
                        UUID.randomUUID()
                ).with(user("doctor@example.com").roles("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Bệnh nhân yêu cầu\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldRescheduleOwnedBookedAppointment() throws Exception {
        UserPrincipal principal = patientPrincipal();
        UUID appointmentId = UUID.randomUUID();
        RescheduleAppointmentResponse response = new RescheduleAppointmentResponse(
                appointmentId,
                AppointmentStatus.BOOKED,
                OffsetDateTime.parse("2026-10-12T13:00:00+07:00"),
                OffsetDateTime.parse("2026-10-12T15:00:00+07:00"),
                OffsetDateTime.parse("2026-10-12T16:00:00+07:00"),
                "Bận việc gia đình",
                OffsetDateTime.parse("2026-10-12T10:00:00+07:00")
        );
        when(appointmentRescheduleService.reschedule(
                eq(principal.getId()),
                eq(appointmentId),
                any(RescheduleAppointmentRequest.class)
        )).thenReturn(response);

        mockMvc.perform(patch("/api/patient/appointments/{id}/reschedule", appointmentId)
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scheduledStartAt": "2026-10-12T15:00:00+07:00",
                                  "reason": "Bận việc gia đình"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đổi lịch hẹn thành công"))
                .andExpect(jsonPath("$.data.id").value(appointmentId.toString()))
                .andExpect(jsonPath("$.data.status").value("BOOKED"))
                .andExpect(jsonPath("$.data.oldScheduledStartAt")
                        .value("2026-10-12T13:00:00+07:00"))
                .andExpect(jsonPath("$.data.scheduledStartAt")
                        .value("2026-10-12T15:00:00+07:00"))
                .andExpect(jsonPath("$.data.scheduledEndAt")
                        .value("2026-10-12T16:00:00+07:00"));

        verify(appointmentRescheduleService).reschedule(
                eq(principal.getId()),
                eq(appointmentId),
                any(RescheduleAppointmentRequest.class)
        );
    }

    @Test
    void shouldValidateRescheduleFields() throws Exception {
        mockMvc.perform(patch(
                        "/api/patient/appointments/{id}/reschedule",
                        UUID.randomUUID()
                ).with(user(patientPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void shouldRejectMalformedRescheduleTime() throws Exception {
        mockMvc.perform(patch(
                        "/api/patient/appointments/{id}/reschedule",
                        UUID.randomUUID()
                ).with(user(patientPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scheduledStartAt": "2026-10-12 15:00",
                                  "reason": "Bận việc gia đình"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private String validBody() {
        return validBody(UUID.randomUUID(), UUID.randomUUID());
    }

    private String validBody(UUID doctorId, UUID serviceId) {
        return """
                {
                  "doctorId": "%s",
                  "serviceId": "%s",
                  "scheduledStartAt": "2026-10-12T13:00:00+07:00"
                }
                """.formatted(doctorId, serviceId);
    }

    private BookAppointmentResponse response(UUID doctorId, UUID serviceId) {
        return new BookAppointmentResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                doctorId,
                serviceId,
                OffsetDateTime.parse("2026-10-12T13:00:00+07:00"),
                OffsetDateTime.parse("2026-10-12T14:00:00+07:00"),
                AppointmentStatus.BOOKED,
                Instant.parse("2026-10-12T03:01:00Z")
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
                OffsetDateTime.parse("2026-10-12T13:00:00+07:00"),
                OffsetDateTime.parse("2026-10-12T14:00:00+07:00"),
                AppointmentStatus.BOOKED,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-10-12T03:01:00Z"),
                Instant.parse("2026-10-12T03:01:00Z")
        );
    }

    private UserPrincipal patientPrincipal() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("patient@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setFullName("Bệnh nhân Nguyễn Văn Bình");
        user.setRole(Role.PATIENT);
        user.setStatus(UserStatus.ACTIVE);
        return UserPrincipal.from(user);
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
