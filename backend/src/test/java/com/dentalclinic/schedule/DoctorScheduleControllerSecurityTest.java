package com.dentalclinic.schedule;

import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.schedule.controller.DoctorScheduleController;
import com.dentalclinic.schedule.controller.PublicDoctorScheduleController;
import com.dentalclinic.schedule.dto.DoctorScheduleRequest;
import com.dentalclinic.schedule.dto.DoctorScheduleResponse;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.service.DoctorScheduleService;
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
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({DoctorScheduleController.class, PublicDoctorScheduleController.class})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class DoctorScheduleControllerSecurityTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DoctorScheduleService doctorScheduleService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "doctor-schedule-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldAllowAnonymousPublicScheduleQuery() throws Exception {
        DoctorScheduleResponse response = response();
        when(doctorScheduleService.getPublicSchedule(response.doctorId()))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/doctors/{doctorId}/schedule", response.doctorId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(response.id().toString()))
                .andExpect(jsonPath("$.data[0].session").value("MORNING"));
    }

    @Test
    void shouldRequireAuthenticationForDoctorSchedules() throws Exception {
        mockMvc.perform(get("/api/doctor/schedules"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldRejectPatientForDoctorSchedules() throws Exception {
        mockMvc.perform(get("/api/doctor/schedules")
                        .with(user("patient@example.com").roles("PATIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldReturnAuthenticatedDoctorSchedules() throws Exception {
        UserPrincipal principal = doctorPrincipal();
        DoctorScheduleResponse response = response();
        when(doctorScheduleService.getMySchedules(principal.getId())).thenReturn(List.of(response));

        mockMvc.perform(get("/api/doctor/schedules").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].doctorId").value(response.doctorId().toString()));
    }

    @Test
    void shouldCreateScheduleForAuthenticatedDoctor() throws Exception {
        UserPrincipal principal = doctorPrincipal();
        DoctorScheduleResponse response = response();
        when(doctorScheduleService.createSchedule(
                eq(principal.getId()),
                any(DoctorScheduleRequest.class)
        )).thenReturn(response);

        mockMvc.perform(post("/api/doctor/schedules")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(response.id().toString()));
    }

    @Test
    void shouldUpdateOwnedSchedule() throws Exception {
        UserPrincipal principal = doctorPrincipal();
        DoctorScheduleResponse response = response();
        when(doctorScheduleService.updateSchedule(
                eq(principal.getId()),
                eq(response.id()),
                any(DoctorScheduleRequest.class)
        )).thenReturn(response);

        mockMvc.perform(put("/api/doctor/schedules/{id}", response.id())
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(response.id().toString()));
    }

    @Test
    void shouldDeleteOwnedSchedule() throws Exception {
        UserPrincipal principal = doctorPrincipal();
        UUID scheduleId = UUID.randomUUID();

        mockMvc.perform(delete("/api/doctor/schedules/{id}", scheduleId)
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(doctorScheduleService).deleteSchedule(principal.getId(), scheduleId);
    }

    @Test
    void shouldValidateScheduleRequest() throws Exception {
        mockMvc.perform(post("/api/doctor/schedules")
                        .with(user(doctorPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldRejectDayOutsideWeeklyRange() throws Exception {
        mockMvc.perform(post("/api/doctor/schedules")
                        .with(user(doctorPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dayOfWeek": 8,
                                  "session": "MORNING",
                                  "startTime": "07:00",
                                  "endTime": "12:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldRejectUnknownSessionValue() throws Exception {
        mockMvc.perform(post("/api/doctor/schedules")
                        .with(user(doctorPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dayOfWeek": 1,
                                  "session": "EVENING",
                                  "startTime": "18:00",
                                  "endTime": "20:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private String validBody() {
        return """
                {
                  "dayOfWeek": 1,
                  "session": "MORNING",
                  "startTime": "07:00",
                  "endTime": "12:00"
                }
                """;
    }

    private DoctorScheduleResponse response() {
        return new DoctorScheduleResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                ScheduleSession.MORNING,
                LocalTime.of(7, 0),
                LocalTime.of(12, 0),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
    }

    private UserPrincipal doctorPrincipal() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("doctor@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setFullName("Bác sĩ Nguyễn Văn An");
        user.setRole(Role.DOCTOR);
        user.setStatus(UserStatus.ACTIVE);
        return UserPrincipal.from(user);
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
