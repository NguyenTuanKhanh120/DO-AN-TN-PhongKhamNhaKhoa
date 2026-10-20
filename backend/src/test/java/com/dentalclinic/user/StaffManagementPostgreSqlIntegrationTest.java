package com.dentalclinic.user;

import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("development")
@Transactional
class StaffManagementPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = Encoders.BASE64.encode(
            Jwts.SIG.HS256.key().build().getEncoded()
    );
    private static final String PASSWORD = "StrongPassword123!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User admin;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "staff-management-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @BeforeEach
    void setUp() {
        String marker = UUID.randomUUID().toString();
        admin = saveUser(
                "phase263-admin-" + marker + "@example.com",
                "Quản trị viên Phase 26.3",
                Role.ADMIN
        );
    }

    @Test
    void shouldCreateListLoginAndDeactivateStaff() throws Exception {
        String adminToken = login(admin.getEmail(), PASSWORD);
        String staffEmail = "phase263-staff-" + UUID.randomUUID() + "@example.com";

        MvcResult createResult = mockMvc.perform(post("/api/admin/staff")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "email", staffEmail,
                                "password", PASSWORD,
                                "fullName", "Nhân viên Phase 26.3"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("STAFF"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();

        JsonNode created = objectMapper.readTree(
                createResult.getResponse().getContentAsByteArray()
        ).path("data");
        UUID staffId = UUID.fromString(created.path("id").asText());
        User staff = userRepository.findById(staffId).orElseThrow();
        assertThat(staff.getRole()).isEqualTo(Role.STAFF);
        assertThat(passwordEncoder.matches(PASSWORD, staff.getPasswordHash())).isTrue();

        mockMvc.perform(get("/api/admin/staff")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("search", staffEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(staffId.toString()));

        String staffToken = login(staffEmail, PASSWORD);
        assertThat(staffToken).isNotBlank();

        mockMvc.perform(patch("/api/admin/staff/{id}/status", staffId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "email", staffEmail,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
    }

    private User saveUser(String email, String fullName, Role role) {
        User user = new User();
        user.setEmail(email.toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName(fullName);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("data")
                .path("accessToken")
                .asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
