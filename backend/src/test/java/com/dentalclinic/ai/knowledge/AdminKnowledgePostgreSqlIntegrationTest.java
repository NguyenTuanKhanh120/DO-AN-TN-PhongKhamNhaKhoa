package com.dentalclinic.ai.knowledge;

import com.dentalclinic.ai.knowledge.entity.KnowledgeFileType;
import com.dentalclinic.ai.knowledge.entity.KnowledgeSource;
import com.dentalclinic.ai.knowledge.entity.KnowledgeSourceStatus;
import com.dentalclinic.ai.knowledge.repository.KnowledgeSourceRepository;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("development")
class AdminKnowledgePostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KnowledgeSourceRepository sourceRepository;

    private UUID createdSourceId;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "admin-knowledge-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 15);
        registry.add("app.ai.enabled", () -> false);
    }

    @AfterEach
    void cleanUp() {
        if (createdSourceId != null) {
            sourceRepository.deleteById(createdSourceId);
            createdSourceId = null;
        }
    }

    @Test
    void shouldExposeStatusButRejectSyncAndSearchBeforeMutationWhenAiIsDisabled() throws Exception {
        String sourceKey = "admin-status-" + UUID.randomUUID();
        KnowledgeSource source = new KnowledgeSource();
        source.setSourceKey(sourceKey);
        source.setFileName("nidcr_dental_knowledge_vi.json");
        source.setFileType(KnowledgeFileType.JSON);
        source.setStatus(KnowledgeSourceStatus.PENDING);
        source.setClinicalReviewStatus("REVIEWED");
        createdSourceId = sourceRepository.saveAndFlush(source).getId();
        UserPrincipal admin = adminPrincipal();

        mockMvc.perform(get("/api/admin/ai/knowledge/status").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiEnabled").value(false))
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.sources[*].sourceKey", hasItem(sourceKey)));

        mockMvc.perform(post("/api/admin/ai/knowledge/sync").with(user(admin)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_BASE_UNAVAILABLE"));

        mockMvc.perform(post("/api/admin/ai/knowledge/search-test")
                        .with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"đau răng\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_BASE_UNAVAILABLE"));

        KnowledgeSource unchanged = sourceRepository.findById(createdSourceId).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(KnowledgeSourceStatus.PENDING);
        assertThat(unchanged.getSyncToken()).isNull();
    }

    private UserPrincipal adminPrincipal() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("admin-knowledge@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setFullName("Admin Knowledge");
        user.setRole(Role.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        return UserPrincipal.from(user);
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
