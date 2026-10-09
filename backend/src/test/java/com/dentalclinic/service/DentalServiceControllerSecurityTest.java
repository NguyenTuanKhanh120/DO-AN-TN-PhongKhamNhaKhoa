package com.dentalclinic.service;

import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.service.controller.AdminDentalServiceController;
import com.dentalclinic.service.controller.DentalServiceController;
import com.dentalclinic.service.dto.CreateDentalServiceRequest;
import com.dentalclinic.service.dto.DentalServiceResponse;
import com.dentalclinic.service.dto.UpdateDentalServiceRequest;
import com.dentalclinic.service.service.DentalServiceManagementService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({DentalServiceController.class, AdminDentalServiceController.class})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class DentalServiceControllerSecurityTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DentalServiceManagementService dentalServiceManagementService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "dental-service-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldAllowAnonymousActiveServiceList() throws Exception {
        PageResponse<DentalServiceResponse> page = new PageResponse<>(
                List.of(response(true)), 0, 20, 1, 1, true, true, false, false
        );
        when(dentalServiceManagementService.getServices(null, true, 0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].active").value(true));
    }

    @Test
    void shouldAllowAnonymousServiceDetail() throws Exception {
        DentalServiceResponse response = response(true);
        when(dentalServiceManagementService.getService(response.id())).thenReturn(response);

        mockMvc.perform(get("/api/services/{id}", response.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(response.id().toString()))
                .andExpect(jsonPath("$.data.price").value(300000.0));
    }

    @Test
    void shouldRequireAuthenticationForAdminCreate() throws Exception {
        mockMvc.perform(post("/api/admin/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldRejectDoctorForAdminCreate() throws Exception {
        mockMvc.perform(post("/api/admin/services")
                        .with(user("doctor@example.com").roles("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldCreateServiceForAdmin() throws Exception {
        DentalServiceResponse response = response(true);
        when(dentalServiceManagementService.createService(any(CreateDentalServiceRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/admin/services")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(response.id().toString()))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void shouldUpdateServiceForAdmin() throws Exception {
        DentalServiceResponse response = response(true);
        when(dentalServiceManagementService.updateService(
                eq(response.id()),
                any(UpdateDentalServiceRequest.class)
        )).thenReturn(response);

        mockMvc.perform(put("/api/admin/services/{id}", response.id())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(response.name()));

        verify(dentalServiceManagementService).updateService(
                eq(response.id()),
                any(UpdateDentalServiceRequest.class)
        );
    }

    @Test
    void shouldSoftDisableServiceForAdmin() throws Exception {
        DentalServiceResponse response = response(false);
        when(dentalServiceManagementService.updateStatus(response.id(), false)).thenReturn(response);

        mockMvc.perform(patch("/api/admin/services/{id}/status", response.id())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    void shouldRejectHardDeleteEndpoint() throws Exception {
        mockMvc.perform(delete("/api/admin/services/{id}", UUID.randomUUID())
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void shouldValidatePriceAndName() throws Exception {
        mockMvc.perform(post("/api/admin/services")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"price\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldValidatePublicPagination() throws Exception {
        mockMvc.perform(get("/api/services").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private DentalServiceResponse response(boolean active) {
        return new DentalServiceResponse(
                UUID.randomUUID(),
                "Cạo vôi răng",
                "Làm sạch cao răng",
                new BigDecimal("300000.00"),
                active,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
    }

    private String validBody() {
        return """
                {
                  "name": "Cạo vôi răng",
                  "description": "Làm sạch cao răng",
                  "price": 300000.00
                }
                """;
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
