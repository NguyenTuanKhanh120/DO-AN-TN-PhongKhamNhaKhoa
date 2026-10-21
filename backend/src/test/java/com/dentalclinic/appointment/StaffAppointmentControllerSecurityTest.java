package com.dentalclinic.appointment;

import com.dentalclinic.appointment.controller.StaffAppointmentController;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.service.AppointmentQueryService;
import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.service.AuthSessionService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.config.SecurityConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaffAppointmentController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class StaffAppointmentControllerSecurityTest {

    private static final String TEST_JWT_SECRET = Encoders.BASE64.encode(
            Jwts.SIG.HS256.key().build().getEncoded()
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppointmentQueryService appointmentQueryService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private AuthSessionService authSessionService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "staff-appointment-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldRequireStaffRole() throws Exception {
        mockMvc.perform(get("/api/staff/appointments"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/staff/appointments")
                        .with(user("doctor@example.com").roles("DOCTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowStaffToViewAllDoctorAppointments() throws Exception {
        UUID doctorId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 20);
        PageResponse<com.dentalclinic.appointment.dto.AppointmentResponse> response =
                new PageResponse<>(List.of(), 0, 20, 0, 0, true, true, false, false);
        when(appointmentQueryService.getStaffAppointments(
                "Nguyễn",
                AppointmentStatus.BOOKED,
                doctorId,
                null,
                date,
                date,
                0,
                20,
                "scheduledStartAt",
                Sort.Direction.ASC
        )).thenReturn(response);

        mockMvc.perform(get("/api/staff/appointments")
                        .with(user("staff@example.com").roles("STAFF"))
                        .param("search", "Nguyễn")
                        .param("status", "BOOKED")
                        .param("doctorId", doctorId.toString())
                        .param("fromDate", date.toString())
                        .param("toDate", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        verify(appointmentQueryService).getStaffAppointments(
                "Nguyễn",
                AppointmentStatus.BOOKED,
                doctorId,
                null,
                date,
                date,
                0,
                20,
                "scheduledStartAt",
                Sort.Direction.ASC
        );
    }

    @Test
    void shouldNotAllowStaffToOpenDoctorMedicalRecordApi() throws Exception {
        mockMvc.perform(get(
                        "/api/doctor/appointments/{id}/medical-record",
                        UUID.randomUUID()
                ).with(user("staff@example.com").roles("STAFF")))
                .andExpect(status().isForbidden());
    }
}
