package com.dentalclinic.schedule;

import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.auth.service.AuthSessionService;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.schedule.controller.DoctorCalendarController;
import com.dentalclinic.schedule.dto.CalendarAvailabilityStatus;
import com.dentalclinic.schedule.dto.DoctorCalendarDayResponse;
import com.dentalclinic.schedule.dto.DoctorCalendarResponse;
import com.dentalclinic.schedule.dto.DoctorCalendarSessionResponse;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.service.SlotService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DoctorCalendarController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class DoctorCalendarControllerSecurityTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SlotService slotService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private AuthSessionService authSessionService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "doctor-calendar-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldAllowAnonymousCalendarQuery() throws Exception {
        UUID doctorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        LocalDate fromDate = LocalDate.of(2026, 10, 19);
        LocalDate toDate = fromDate.plusDays(29);
        DoctorCalendarResponse response = new DoctorCalendarResponse(
                doctorId,
                serviceId,
                fromDate,
                toDate,
                List.of(new DoctorCalendarDayResponse(
                        fromDate,
                        1,
                        CalendarAvailabilityStatus.AVAILABLE,
                        List.of(new DoctorCalendarSessionResponse(
                                ScheduleSession.MORNING,
                                CalendarAvailabilityStatus.AVAILABLE,
                                0,
                                List.of()
                        ))
                ))
        );
        when(slotService.getCalendar(doctorId, serviceId, fromDate, toDate)).thenReturn(response);

        mockMvc.perform(get("/api/doctors/{doctorId}/calendar", doctorId)
                        .param("serviceId", serviceId.toString())
                        .param("fromDate", fromDate.toString())
                        .param("toDate", toDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.doctorId").value(doctorId.toString()))
                .andExpect(jsonPath("$.data.days[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.days[0].patientId").doesNotExist());

        verify(slotService).getCalendar(doctorId, serviceId, fromDate, toDate);
    }

    @Test
    void shouldRequireServiceId() throws Exception {
        mockMvc.perform(get("/api/doctors/{doctorId}/calendar", UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldNotExposeRemovedDoctorDayOffEndpoints() throws Exception {
        UUID doctorId = UUID.randomUUID();
        UUID dayOffId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/doctors/{doctorId}/days-off/preview", doctorId))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/doctors/{doctorId}/days-off", doctorId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/doctors/{doctorId}/days-off", doctorId))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(
                        "/api/admin/doctors/{doctorId}/days-off/{dayOffId}",
                        doctorId,
                        dayOffId
                ))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/doctors/{doctorId}/days-off", doctorId))
                .andExpect(status().isNotFound());
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
