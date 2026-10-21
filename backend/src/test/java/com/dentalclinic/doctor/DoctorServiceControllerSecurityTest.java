package com.dentalclinic.doctor;

import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.service.AuthSessionService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.doctor.controller.AdminDoctorServiceController;
import com.dentalclinic.doctor.controller.DoctorServiceController;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.service.DoctorServiceMappingService;
import com.dentalclinic.service.dto.DentalServiceResponse;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({DoctorServiceController.class, AdminDoctorServiceController.class})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class DoctorServiceControllerSecurityTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DoctorServiceMappingService doctorServiceMappingService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private AuthSessionService authSessionService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "doctor-service-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldAllowAnonymousDoctorServicesQuery() throws Exception {
        UUID doctorId = UUID.randomUUID();
        DentalServiceResponse response = serviceResponse();
        when(doctorServiceMappingService.getServicesByDoctor(doctorId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/doctors/{doctorId}/services", doctorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(response.id().toString()))
                .andExpect(jsonPath("$.data[0].name").value(response.name()));
    }

    @Test
    void shouldAllowAnonymousServiceDoctorsQuery() throws Exception {
        UUID serviceId = UUID.randomUUID();
        DoctorResponse response = doctorResponse();
        when(doctorServiceMappingService.getDoctorsByService(serviceId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/services/{serviceId}/doctors", serviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(response.id().toString()))
                .andExpect(jsonPath("$.data[0].fullName").value(response.fullName()));
    }

    @Test
    void shouldRequireAuthenticationForMappingUpdate() throws Exception {
        mockMvc.perform(put("/api/admin/doctors/{doctorId}/services", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceIds\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldRejectDoctorForMappingUpdate() throws Exception {
        mockMvc.perform(put("/api/admin/doctors/{doctorId}/services", UUID.randomUUID())
                        .with(user("doctor@example.com").roles("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceIds\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldReplaceMappingsForAdmin() throws Exception {
        UUID doctorId = UUID.randomUUID();
        DentalServiceResponse response = serviceResponse();
        when(doctorServiceMappingService.replaceDoctorServices(
                doctorId,
                List.of(response.id())
        )).thenReturn(List.of(response));

        mockMvc.perform(put("/api/admin/doctors/{doctorId}/services", doctorId)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceIds\":[\"" + response.id() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(response.id().toString()));

        verify(doctorServiceMappingService).replaceDoctorServices(
                doctorId,
                List.of(response.id())
        );
    }

    @Test
    void shouldAllowAdminToClearMappings() throws Exception {
        UUID doctorId = UUID.randomUUID();
        when(doctorServiceMappingService.replaceDoctorServices(doctorId, List.of()))
                .thenReturn(List.of());

        mockMvc.perform(put("/api/admin/doctors/{doctorId}/services", doctorId)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void shouldRejectMissingServiceIdsField() throws Exception {
        mockMvc.perform(put("/api/admin/doctors/{doctorId}/services", UUID.randomUUID())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldRejectNullServiceId() throws Exception {
        mockMvc.perform(put("/api/admin/doctors/{doctorId}/services", UUID.randomUUID())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceIds\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private DentalServiceResponse serviceResponse() {
        return new DentalServiceResponse(
                UUID.randomUUID(),
                "Cạo vôi răng",
                "Làm sạch cao răng",
                new BigDecimal("300000.00"),
                true,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
    }

    private DoctorResponse doctorResponse() {
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

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
