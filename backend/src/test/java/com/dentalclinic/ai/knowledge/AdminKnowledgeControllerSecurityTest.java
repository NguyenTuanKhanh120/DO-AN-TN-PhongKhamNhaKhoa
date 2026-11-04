package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.controller.AdminKnowledgeController;
import com.dentalclinic.ai.knowledge.dto.KnowledgeSearchTestRequest;
import com.dentalclinic.ai.knowledge.dto.KnowledgeSearchTestResponse;
import com.dentalclinic.ai.knowledge.dto.KnowledgeStatusResponse;
import com.dentalclinic.ai.knowledge.dto.KnowledgeSyncResponse;
import com.dentalclinic.ai.knowledge.model.KnowledgeSyncOutcome;
import com.dentalclinic.ai.knowledge.service.AdminKnowledgeService;
import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.auth.service.AuthSessionService;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.config.SecurityConfig;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminKnowledgeController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class AdminKnowledgeControllerSecurityTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final Instant NOW = Instant.parse("2026-10-30T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminKnowledgeService adminKnowledgeService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private AuthSessionService authSessionService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "admin-knowledge-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldRequireAuthenticationForAllKnowledgeEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/ai/knowledge/status"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/ai/knowledge/sync"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/ai/knowledge/search-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"đau răng\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectNonAdminForAllKnowledgeEndpoints() throws Exception {
        var doctor = user("doctor@example.com").roles("DOCTOR");
        mockMvc.perform(get("/api/admin/ai/knowledge/status").with(doctor))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/ai/knowledge/sync").with(doctor))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/ai/knowledge/search-test")
                        .with(doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"đau răng\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnStatusToAdmin() throws Exception {
        KnowledgeStatusResponse response = new KnowledgeStatusResponse(
                true,
                true,
                1,
                1,
                0,
                0,
                0,
                42,
                NOW,
                List.of()
        );
        when(adminKnowledgeService.getStatus()).thenReturn(response);

        mockMvc.perform(get("/api/admin/ai/knowledge/status").with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiEnabled").value(true))
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.totalChunks").value(42));

        verify(adminKnowledgeService).getStatus();
    }

    @Test
    void shouldAllowAdminToTriggerSync() throws Exception {
        when(adminKnowledgeService.synchronize()).thenReturn(new KnowledgeSyncResponse(
                KnowledgeSyncOutcome.UNCHANGED,
                NOW
        ));

        mockMvc.perform(post("/api/admin/ai/knowledge/sync").with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("UNCHANGED"));

        verify(adminKnowledgeService).synchronize();
    }

    @Test
    void shouldAllowAdminToTestSearchAndValidateRequest() throws Exception {
        KnowledgeSearchTestRequest request = new KnowledgeSearchTestRequest(
                "đau răng",
                3,
                0.6,
                null
        );
        when(adminKnowledgeService.searchTest(request)).thenReturn(
                new KnowledgeSearchTestResponse("đau răng", 3, 0.6, 0, List.of())
        );

        mockMvc.perform(post("/api/admin/ai/knowledge/search-test")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "đau răng",
                                  "topK": 3,
                                  "minimumSimilarity": 0.6
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.query").value("đau răng"))
                .andExpect(jsonPath("$.data.resultCount").value(0));

        mockMvc.perform(post("/api/admin/ai/knowledge/search-test")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"   \",\"topK\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(adminKnowledgeService).searchTest(request);
        verify(adminKnowledgeService, never()).searchTest(new KnowledgeSearchTestRequest(
                "   ",
                0,
                null,
                null
        ));
    }

    private UserPrincipal adminPrincipal() {
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
