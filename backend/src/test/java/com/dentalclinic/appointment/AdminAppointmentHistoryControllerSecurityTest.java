package com.dentalclinic.appointment;

import com.dentalclinic.appointment.controller.AdminAppointmentController;
import com.dentalclinic.appointment.dto.AppointmentHistoryResponse;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.service.AppointmentHistoryService;
import com.dentalclinic.appointment.service.AppointmentQueryService;
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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAppointmentController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class AdminAppointmentHistoryControllerSecurityTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppointmentQueryService appointmentQueryService;

    @MockitoBean
    private AppointmentHistoryService appointmentHistoryService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "appointment-history-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get(
                        "/api/admin/appointments/{id}/history",
                        UUID.randomUUID()
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldRejectNonAdmin() throws Exception {
        mockMvc.perform(get(
                        "/api/admin/appointments/{id}/history",
                        UUID.randomUUID()
                ).with(user("doctor@example.com").roles("DOCTOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldReturnAppointmentHistoryToAdmin() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        AppointmentHistoryResponse response = new AppointmentHistoryResponse(
                UUID.randomUUID(),
                appointmentId,
                AppointmentAction.RESCHEDULE,
                actorId,
                "Bệnh nhân Nguyễn",
                Role.PATIENT,
                "Bận việc gia đình",
                JsonNodeFactory.instance.objectNode()
                        .put("old_start", "2026-10-15T14:00:00+07:00")
                        .put("new_start", "2026-10-15T16:00:00+07:00"),
                Instant.parse("2026-10-12T03:00:00Z")
        );
        when(appointmentHistoryService.getHistory(appointmentId))
                .thenReturn(List.of(response));

        mockMvc.perform(get(
                        "/api/admin/appointments/{id}/history",
                        appointmentId
                ).with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].appointmentId")
                        .value(appointmentId.toString()))
                .andExpect(jsonPath("$.data[0].action").value("RESCHEDULE"))
                .andExpect(jsonPath("$.data[0].changedByUserId")
                        .value(actorId.toString()))
                .andExpect(jsonPath("$.data[0].changedByFullName")
                        .value("Bệnh nhân Nguyễn"))
                .andExpect(jsonPath("$.data[0].changedByRole").value("PATIENT"))
                .andExpect(jsonPath("$.data[0].reason").value("Bận việc gia đình"))
                .andExpect(jsonPath("$.data[0].details.old_start")
                        .value("2026-10-15T14:00:00+07:00"));

        verify(appointmentHistoryService).getHistory(appointmentId);
    }

    @Test
    void shouldRejectMalformedAppointmentId() throws Exception {
        mockMvc.perform(get("/api/admin/appointments/not-a-uuid/history")
                        .with(user(principal())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private UserPrincipal principal() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("admin@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setFullName("Quản trị viên");
        user.setRole(Role.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        return UserPrincipal.from(user);
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
