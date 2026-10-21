package com.dentalclinic.schedule;

import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.service.AuthSessionService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.schedule.controller.AvailableSlotController;
import com.dentalclinic.schedule.dto.AvailableSlotResponse;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.service.SlotService;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AvailableSlotController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class AvailableSlotControllerSecurityTest {

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
        registry.add("security.jwt.issuer", () -> "available-slot-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldAllowAnonymousAvailableSlotQuery() throws Exception {
        UUID doctorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 12);
        AvailableSlotResponse response = response(date);
        when(slotService.getAvailableSlots(doctorId, serviceId, date))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/doctors/{doctorId}/available-slots", doctorId)
                        .param("serviceId", serviceId.toString())
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].date").value("2026-10-12"))
                .andExpect(jsonPath("$.data[0].session").value("AFTERNOON"))
                .andExpect(jsonPath("$.data[0].startTime").value("13:00:00"))
                .andExpect(jsonPath("$.data[0].startAt").value("2026-10-12T13:00:00+07:00"));

        verify(slotService).getAvailableSlots(doctorId, serviceId, date);
    }

    @Test
    void shouldAllowAuthenticatedPatientToQueryPublicSlots() throws Exception {
        UUID doctorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 12);
        when(slotService.getAvailableSlots(doctorId, serviceId, date)).thenReturn(List.of());

        mockMvc.perform(get("/api/doctors/{doctorId}/available-slots", doctorId)
                        .with(user("patient@example.com").roles("PATIENT"))
                        .param("serviceId", serviceId.toString())
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void shouldRequireServiceId() throws Exception {
        mockMvc.perform(get("/api/doctors/{doctorId}/available-slots", UUID.randomUUID())
                        .param("date", "2026-10-12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("serviceId"));
    }

    @Test
    void shouldRequireDate() throws Exception {
        mockMvc.perform(get("/api/doctors/{doctorId}/available-slots", UUID.randomUUID())
                        .param("serviceId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("date"));
    }

    @Test
    void shouldRejectMalformedDate() throws Exception {
        mockMvc.perform(get("/api/doctors/{doctorId}/available-slots", UUID.randomUUID())
                        .param("serviceId", UUID.randomUUID().toString())
                        .param("date", "12-10-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("date"));
    }

    @Test
    void shouldRejectMalformedServiceId() throws Exception {
        mockMvc.perform(get("/api/doctors/{doctorId}/available-slots", UUID.randomUUID())
                        .param("serviceId", "not-a-uuid")
                        .param("date", "2026-10-12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("serviceId"));
    }

    private AvailableSlotResponse response(LocalDate date) {
        return new AvailableSlotResponse(
                date,
                ScheduleSession.AFTERNOON,
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                OffsetDateTime.parse("2026-10-12T13:00:00+07:00"),
                OffsetDateTime.parse("2026-10-12T14:00:00+07:00")
        );
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
