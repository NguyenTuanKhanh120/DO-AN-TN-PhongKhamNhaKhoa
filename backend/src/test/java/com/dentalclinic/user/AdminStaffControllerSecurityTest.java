package com.dentalclinic.user;

import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.user.controller.AdminStaffController;
import com.dentalclinic.user.dto.AdminUserResponse;
import com.dentalclinic.user.dto.CreateStaffRequest;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminStaffController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class AdminStaffControllerSecurityTest {

    private static final String TEST_JWT_SECRET = Encoders.BASE64.encode(
            Jwts.SIG.HS256.key().build().getEncoded()
    );

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
        registry.add("security.jwt.issuer", () -> "admin-staff-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldRequireAdminForStaffManagement() throws Exception {
        mockMvc.perform(get("/api/admin/staff"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/staff")
                        .with(user("staff@example.com").roles("STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldCreateStaffForAdmin() throws Exception {
        UserPrincipal admin = principal(Role.ADMIN);
        CreateStaffRequest request = new CreateStaffRequest(
                "staff@example.com",
                "StrongPassword123!",
                "Nhân viên lễ tân"
        );
        AdminUserResponse response = response(UserStatus.ACTIVE);
        when(adminUserService.createStaff(admin.getId(), request)).thenReturn(response);

        mockMvc.perform(post("/api/admin/staff")
                        .with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "staff@example.com",
                                  "password": "StrongPassword123!",
                                  "fullName": "Nhân viên lễ tân"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("STAFF"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        verify(adminUserService).createStaff(admin.getId(), request);
    }

    @Test
    void shouldListAndDeactivateStaffForAdmin() throws Exception {
        UserPrincipal admin = principal(Role.ADMIN);
        AdminUserResponse active = response(UserStatus.ACTIVE);
        AdminUserResponse inactive = new AdminUserResponse(
                active.id(),
                active.email(),
                active.fullName(),
                Role.STAFF,
                UserStatus.INACTIVE,
                active.createdAt(),
                active.updatedAt()
        );
        when(adminUserService.getStaff("lễ tân", UserStatus.ACTIVE, 0, 20))
                .thenReturn(new PageResponse<>(
                        List.of(active), 0, 20, 1, 1, true, true, false, false
                ));
        when(adminUserService.updateStaffStatus(
                admin.getId(),
                active.id(),
                UserStatus.INACTIVE
        )).thenReturn(inactive);

        mockMvc.perform(get("/api/admin/staff")
                        .with(user(admin))
                        .param("search", "lễ tân")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].role").value("STAFF"));

        mockMvc.perform(patch("/api/admin/staff/{id}/status", active.id())
                        .with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    private AdminUserResponse response(UserStatus status) {
        return new AdminUserResponse(
                UUID.randomUUID(),
                "staff@example.com",
                "Nhân viên lễ tân",
                Role.STAFF,
                status,
                Instant.parse("2026-10-20T01:00:00Z"),
                Instant.parse("2026-10-20T01:00:00Z")
        );
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
}
