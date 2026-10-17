package com.dentalclinic.schedule;

import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.schedule.controller.AdminDoctorDayOffController;
import com.dentalclinic.schedule.controller.PublicDoctorDayOffController;
import com.dentalclinic.schedule.dto.AdminDoctorDayOffResponse;
import com.dentalclinic.schedule.dto.CreateDoctorDayOffRequest;
import com.dentalclinic.schedule.dto.CreateDoctorDayOffResponse;
import com.dentalclinic.schedule.dto.DoctorDayOffPeriod;
import com.dentalclinic.schedule.dto.PublicDoctorDayOffResponse;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.service.DoctorDayOffService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminDoctorDayOffController.class, PublicDoctorDayOffController.class})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class DoctorDayOffControllerSecurityTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final LocalDate OFF_DATE = LocalDate.of(2026, 10, 19);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DoctorDayOffService doctorDayOffService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "doctor-day-off-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldRequireAuthenticationForAdminCreate() throws Exception {
        mockMvc.perform(post("/api/admin/doctors/{doctorId}/days-off", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldRejectNonAdminCreate() throws Exception {
        mockMvc.perform(post("/api/admin/doctors/{doctorId}/days-off", UUID.randomUUID())
                        .with(user(principal(Role.DOCTOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldCreateDayOffForAuthenticatedAdmin() throws Exception {
        UserPrincipal admin = principal(Role.ADMIN);
        UUID doctorId = UUID.randomUUID();
        AdminDoctorDayOffResponse row = new AdminDoctorDayOffResponse(
                UUID.randomUUID(),
                doctorId,
                "Bác sĩ Nguyễn Văn A",
                OFF_DATE,
                ScheduleSession.MORNING,
                "Bác sĩ nghỉ đột xuất",
                admin.getId(),
                admin.getFullName(),
                Instant.parse("2026-10-17T03:00:00Z")
        );
        CreateDoctorDayOffResponse response = new CreateDoctorDayOffResponse(
                doctorId,
                "Bác sĩ Nguyễn Văn A",
                OFF_DATE,
                DoctorDayOffPeriod.MORNING,
                List.of(row),
                0,
                List.of(),
                0,
                List.of()
        );
        when(doctorDayOffService.create(
                admin.getId(), doctorId, new CreateDoctorDayOffRequest(
                        OFF_DATE, DoctorDayOffPeriod.MORNING, "Bác sĩ nghỉ đột xuất"
                )
        )).thenReturn(response);

        mockMvc.perform(post("/api/admin/doctors/{doctorId}/days-off", doctorId)
                        .with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.doctorId").value(doctorId.toString()))
                .andExpect(jsonPath("$.data.createdDaysOff[0].reason")
                        .value("Bác sĩ nghỉ đột xuất"));

        verify(doctorDayOffService).create(
                admin.getId(), doctorId, new CreateDoctorDayOffRequest(
                        OFF_DATE, DoctorDayOffPeriod.MORNING, "Bác sĩ nghỉ đột xuất"
                )
        );
    }

    @Test
    void shouldValidateDayOffRequest() throws Exception {
        mockMvc.perform(post("/api/admin/doctors/{doctorId}/days-off", UUID.randomUUID())
                        .with(user(principal(Role.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offDate\":\"2026-10-19\",\"session\":\"MORNING\",\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldAllowPublicReadWithoutExposingInternalReason() throws Exception {
        UUID doctorId = UUID.randomUUID();
        PublicDoctorDayOffResponse response = new PublicDoctorDayOffResponse(
                UUID.randomUUID(), doctorId, OFF_DATE, ScheduleSession.AFTERNOON
        );
        when(doctorDayOffService.getPublicDaysOff(doctorId, OFF_DATE, OFF_DATE))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/doctors/{doctorId}/days-off", doctorId)
                        .param("fromDate", OFF_DATE.toString())
                        .param("toDate", OFF_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].session").value("AFTERNOON"))
                .andExpect(jsonPath("$.data[0].reason").doesNotExist())
                .andExpect(jsonPath("$.data[0].createdByUserId").doesNotExist());
    }

    private String validRequest() {
        return """
                {
                  "offDate": "2026-10-19",
                  "session": "MORNING",
                  "reason": "Bác sĩ nghỉ đột xuất"
                }
                """;
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
