package com.dentalclinic.service;

import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.service.repository.DentalServiceRepository;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
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

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("development")
@Transactional
class DentalServiceManagementPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final String PASSWORD = "StrongPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DentalServiceRepository dentalServiceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "dental-service-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldManageDentalServicesWithoutHardDeleteOnPostgreSql() throws Exception {
        String marker = UUID.randomUUID().toString();
        User admin = saveUser("phase08-admin-" + marker + "@example.com", Role.ADMIN);
        User doctor = saveUser("phase08-doctor-" + marker + "@example.com", Role.DOCTOR);
        String adminToken = login(admin.getEmail());
        String doctorToken = login(doctor.getEmail());

        String serviceName = "Cạo vôi răng " + marker;
        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("name", "  " + serviceName + "  ");
        createBody.put("description", "  Làm sạch cao răng PHASE 08  ");
        createBody.put("price", new BigDecimal("300000.00"));
        createBody.put(
                "imageUrl",
                "  https://cdn.example.com/dental-services/scaling-" + marker + ".webp  "
        );

        mockMvc.perform(post("/api/admin/services")
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createBody)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        MvcResult created = mockMvc.perform(post("/api/admin/services")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(serviceName))
                .andExpect(jsonPath("$.data.description").value("Làm sạch cao răng PHASE 08"))
                .andExpect(jsonPath("$.data.price").value(300000.0))
                .andExpect(jsonPath("$.data.imageUrl").value(
                        "https://cdn.example.com/dental-services/scaling-" + marker + ".webp"
                ))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();

        UUID serviceId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsByteArray())
                        .path("data").path("id").asText()
        );
        DentalService persisted = dentalServiceRepository.findById(serviceId).orElseThrow();
        assertThat(persisted.getName()).isEqualTo(serviceName);
        assertThat(persisted.getPrice()).isEqualByComparingTo("300000.00");
        assertThat(persisted.getImageUrl())
                .isEqualTo("https://cdn.example.com/dental-services/scaling-" + marker + ".webp");
        assertThat(persisted.isActive()).isTrue();

        mockMvc.perform(get("/api/services")
                        .param("search", marker.toUpperCase())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(serviceId.toString()))
                .andExpect(jsonPath("$.data.content[0].imageUrl").value(persisted.getImageUrl()));

        mockMvc.perform(get("/api/services/{id}", serviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(serviceName))
                .andExpect(jsonPath("$.data.imageUrl").value(persisted.getImageUrl()));

        String updatedName = "Tẩy trắng răng " + marker;
        Map<String, Object> updateBody = new LinkedHashMap<>();
        updateBody.put("name", "  " + updatedName + "  ");
        updateBody.put("description", "  Mô tả đã cập nhật  ");
        updateBody.put("price", new BigDecimal("1250000.50"));
        updateBody.put(
                "imageUrl",
                "https://cdn.example.com/dental-services/whitening-" + marker + ".webp"
        );

        mockMvc.perform(put("/api/admin/services/{id}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(updatedName))
                .andExpect(jsonPath("$.data.price").value(1250000.5))
                .andExpect(jsonPath("$.data.imageUrl").value(
                        "https://cdn.example.com/dental-services/whitening-" + marker + ".webp"
                ))
                .andExpect(jsonPath("$.data.active").value(true));

        updateBody.put("imageUrl", null);
        mockMvc.perform(put("/api/admin/services/{id}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value(org.hamcrest.Matchers.nullValue()));
        assertThat(dentalServiceRepository.findById(serviceId).orElseThrow().getImageUrl()).isNull();

        mockMvc.perform(patch("/api/admin/services/{id}/status", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        assertThat(dentalServiceRepository.findById(serviceId).orElseThrow().isActive()).isFalse();

        mockMvc.perform(get("/api/services").param("search", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/services")
                        .param("search", marker)
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(serviceId.toString()));

        long serviceCountBeforeDuplicate = dentalServiceRepository.count();
        createBody.put("name", updatedName);
        createBody.put("description", null);
        createBody.put("price", new BigDecimal("500000.00"));
        mockMvc.perform(post("/api/admin/services")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SERVICE_NAME_ALREADY_EXISTS"));
        assertThat(dentalServiceRepository.count()).isEqualTo(serviceCountBeforeDuplicate);

        mockMvc.perform(delete("/api/admin/services/{id}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        assertThat(dentalServiceRepository.existsById(serviceId)).isTrue();

        mockMvc.perform(put("/api/admin/services/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(updateBody)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        Map<String, Object> legacyCompatibleBody = new LinkedHashMap<>();
        legacyCompatibleBody.put("name", "Dịch vụ không ảnh " + marker);
        legacyCompatibleBody.put("description", "Dữ liệu tương thích dịch vụ cũ");
        legacyCompatibleBody.put("price", new BigDecimal("100000.00"));
        MvcResult legacyCompatibleResult = mockMvc.perform(post("/api/admin/services")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(legacyCompatibleBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.imageUrl").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();
        UUID legacyCompatibleServiceId = UUID.fromString(
                objectMapper.readTree(legacyCompatibleResult.getResponse().getContentAsByteArray())
                        .path("data").path("id").asText()
        );
        assertThat(dentalServiceRepository.findById(legacyCompatibleServiceId).orElseThrow().getImageUrl())
                .isNull();
        mockMvc.perform(get("/api/services/{id}", legacyCompatibleServiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value(org.hamcrest.Matchers.nullValue()));
    }

    private User saveUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName(role.name() + " PHASE 08");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "email", email,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return response.path("data").path("accessToken").asText();
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
