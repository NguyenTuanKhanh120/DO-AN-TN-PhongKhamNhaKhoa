package com.dentalclinic.user;

import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.user.controller.AdminUserController;
import com.dentalclinic.user.dto.AdminUserResponse;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.service.AdminUserService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class AdminUserControllerSecurityTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "admin-user-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldRejectNonAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(user("patient@example.com").roles("PATIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldReturnPagedUsersForAdmin() throws Exception {
        AdminUserResponse item = response(Role.PATIENT, UserStatus.ACTIVE);
        PageResponse<AdminUserResponse> page = new PageResponse<>(
                List.of(item), 0, 20, 1, 1, true, true, false, false
        );
        when(adminUserService.getUsers("an", Role.PATIENT, UserStatus.ACTIVE, 0, 20))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/users")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .param("search", "an")
                        .param("role", "PATIENT")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(item.id().toString()))
                .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist());

        verify(adminUserService).getUsers("an", Role.PATIENT, UserStatus.ACTIVE, 0, 20);
    }

    @Test
    void shouldReturnUserDetailForAdmin() throws Exception {
        AdminUserResponse item = response(Role.DOCTOR, UserStatus.ACTIVE);
        when(adminUserService.getUser(item.id())).thenReturn(item);

        mockMvc.perform(get("/api/admin/users/{id}", item.id())
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(item.id().toString()))
                .andExpect(jsonPath("$.data.role").value("DOCTOR"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void shouldUpdateStatusForAdmin() throws Exception {
        AdminUserResponse item = response(Role.PATIENT, UserStatus.INACTIVE);
        when(adminUserService.updateStatus(item.id(), UserStatus.INACTIVE)).thenReturn(item);

        mockMvc.perform(patch("/api/admin/users/{id}/status", item.id())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        verify(adminUserService).updateStatus(item.id(), UserStatus.INACTIVE);
    }

    @Test
    void shouldValidatePagination() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .param("page", "-1")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldRequireStatusInPatchBody() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/status", UUID.randomUUID())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private AdminUserResponse response(Role role, UserStatus status) {
        return new AdminUserResponse(
                UUID.randomUUID(),
                role.name().toLowerCase() + "@example.com",
                "Người dùng kiểm thử",
                role,
                status,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
