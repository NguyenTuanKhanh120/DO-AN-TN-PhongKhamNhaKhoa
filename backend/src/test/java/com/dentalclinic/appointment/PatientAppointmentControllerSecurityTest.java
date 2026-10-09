package com.dentalclinic.appointment;

import com.dentalclinic.appointment.controller.PatientAppointmentController;
import com.dentalclinic.appointment.dto.BookAppointmentRequest;
import com.dentalclinic.appointment.dto.BookAppointmentResponse;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.service.AppointmentBookingService;
import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
