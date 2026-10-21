package com.dentalclinic.doctor;

import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.service.AuthSessionService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.doctor.controller.AdminDoctorController;
import com.dentalclinic.doctor.controller.DoctorController;
import com.dentalclinic.doctor.dto.AdminDoctorResponse;
import com.dentalclinic.doctor.dto.CreateDoctorRequest;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.doctor.dto.UpdateDoctorRequest;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.service.DoctorManagementService;
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
import java.math.BigDecimal;
import java.time.Instant;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({DoctorController.class, AdminDoctorController.class})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class DoctorControllerSecurityTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DoctorManagementService doctorManagementService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private AuthSessionService authSessionService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "doctor-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldAllowAnonymousDoctorList() throws Exception {
        PageResponse<DoctorResponse> page = new PageResponse<>(
                List.of(publicResponse()), 0, 20, 1, 1, true, true, false, false
        );
        when(doctorManagementService.getDoctors(null, null, null, 0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist());
    }

    @Test
    void shouldAllowAnonymousDoctorDetail() throws Exception {
        DoctorResponse response = publicResponse();
        when(doctorManagementService.getDoctor(response.id())).thenReturn(response);

        mockMvc.perform(get("/api/doctors/{id}", response.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(response.id().toString()))
                .andExpect(jsonPath("$.data.licenseNumber").value(response.licenseNumber()));
    }

    @Test
    void shouldRequireAuthenticationForAdminCreate() throws Exception {
        mockMvc.perform(post("/api/admin/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldRejectPatientForAdminCreate() throws Exception {
        mockMvc.perform(post("/api/admin/doctors")
                        .with(user("patient@example.com").roles("PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldCreateDoctorForAdmin() throws Exception {
        AdminDoctorResponse response = adminResponse(DoctorStatus.ACTIVE);
        when(doctorManagementService.createDoctor(any(CreateDoctorRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/admin/doctors")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(response.id().toString()))
                .andExpect(jsonPath("$.data.email").value(response.email()))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void shouldUpdateDoctorForAdmin() throws Exception {
        AdminDoctorResponse response = adminResponse(DoctorStatus.ACTIVE);
        when(doctorManagementService.updateDoctor(eq(response.id()), any(UpdateDoctorRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/admin/doctors/{id}", response.id())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(response.id().toString()));

        verify(doctorManagementService).updateDoctor(eq(response.id()), any(UpdateDoctorRequest.class));
    }

    @Test
    void shouldUpdateWorkingStatusForAdmin() throws Exception {
        AdminDoctorResponse response = adminResponse(DoctorStatus.ON_LEAVE);
        when(doctorManagementService.updateWorkingStatus(response.id(), DoctorStatus.ON_LEAVE))
                .thenReturn(response);

        mockMvc.perform(patch("/api/admin/doctors/{id}/status", response.id())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ON_LEAVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workingStatus").value("ON_LEAVE"));
    }

    @Test
    void shouldValidateAdminCreateRequest() throws Exception {
        mockMvc.perform(post("/api/admin/doctors")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldValidatePublicPagination() throws Exception {
        mockMvc.perform(get("/api/doctors").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private DoctorResponse publicResponse() {
        return new DoctorResponse(
                UUID.randomUUID(),
                "Bác sĩ Nguyễn Văn An",
                "Nha khoa tổng quát",
                "GP-001",
                "Bác sĩ kiểm thử",
                new BigDecimal("4.5"),
                8,
                DoctorStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
    }

    private AdminDoctorResponse adminResponse(DoctorStatus doctorStatus) {
        return new AdminDoctorResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "doctor@example.com",
                "Bác sĩ Nguyễn Văn An",
                UserStatus.ACTIVE,
                "Nha khoa tổng quát",
                "GP-001",
                "Bác sĩ kiểm thử",
                new BigDecimal("4.5"),
                8,
                doctorStatus,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
    }

    private String validCreateBody() {
        return """
                {
                  "email": "doctor@example.com",
                  "password": "StrongPassword123!",
                  "fullName": "Bác sĩ Nguyễn Văn An",
                  "specialization": "Nha khoa tổng quát",
                  "licenseNumber": "GP-001",
                  "bio": "Bác sĩ kiểm thử",
                  "experienceYears": 8
                }
                """;
    }

    private String validUpdateBody() {
        return """
                {
                  "fullName": "Bác sĩ Nguyễn Văn An",
                  "specialization": "Nha khoa tổng quát",
                  "licenseNumber": "GP-001",
                  "bio": "Bác sĩ kiểm thử",
                  "rating": 4.5,
                  "experienceYears": 8
                }
                """;
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
