package com.dentalclinic.user;

import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.user.dto.UpdateAdminUserRequest;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import com.dentalclinic.user.service.AdminUserService;
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
import org.springframework.transaction.annotation.Propagation;

import jakarta.persistence.EntityManager;

import javax.crypto.SecretKey;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class AdminUserManagementPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final String PASSWORD = "StrongPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AdminUserService adminUserService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "admin-user-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldSearchFilterPageReadAndUpdateUsersWithAdminAuthorization() throws Exception {
        String marker = "phase06-" + UUID.randomUUID();
        User admin = saveUser(marker + "-admin@example.com", "Quản trị viên " + marker,
                Role.ADMIN, UserStatus.ACTIVE);
        User activePatient = saveUser(marker + "-active-patient@example.com", "Bệnh nhân ACTIVE " + marker,
                Role.PATIENT, UserStatus.ACTIVE);
        saveUser(marker + "-inactive-patient@example.com", "Bệnh nhân INACTIVE " + marker,
                Role.PATIENT, UserStatus.INACTIVE);
        User doctor = saveUser(marker + "-doctor@example.com", "Bác sĩ " + marker,
                Role.DOCTOR, UserStatus.ACTIVE);

        String adminToken = login(admin.getEmail(), PASSWORD);
        String patientToken = login(activePatient.getEmail(), PASSWORD);

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(patientToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("search", marker.toUpperCase())
                        .param("role", "PATIENT")
                        .param("status", "ACTIVE")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(activePatient.getId().toString()))
                .andExpect(jsonPath("$.data.content[0].email").value(activePatient.getEmail()))
                .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist());

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("search", marker)
                        .param("role", "PATIENT")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        mockMvc.perform(get("/api/admin/users/{id}", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(doctor.getId().toString()))
                .andExpect(jsonPath("$.data.role").value("DOCTOR"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        mockMvc.perform(patch("/api/admin/users/{id}/status", activePatient.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        User updatedPatient = userRepository.findById(activePatient.getId()).orElseThrow();
        assertThat(updatedPatient.getStatus()).isEqualTo(UserStatus.INACTIVE);

        mockMvc.perform(get("/api/patients/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(patientToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));

        mockMvc.perform(get("/api/admin/users/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldUpdatePatientAccountAndLinkedProfileWithoutChangingRole() throws Exception {
        String marker = "admin-user-update-" + UUID.randomUUID();
        User admin = saveUser(marker + "-admin@example.com", "Quản trị viên",
                Role.ADMIN, UserStatus.ACTIVE);
        User patientUser = saveUser(marker + "-patient@example.com", "Tên cũ",
                Role.PATIENT, UserStatus.ACTIVE);
        Patient patient = new Patient();
        patient.setUser(patientUser);
        patient.setFullName("Tên cũ");
        patientRepository.saveAndFlush(patient);
        String adminToken = login(admin.getEmail(), PASSWORD);

        mockMvc.perform(put("/api/admin/users/{id}", patientUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "  Nguyễn   Văn A  ",
                                  "email": " PATIENT.UPDATED@Example.com ",
                                  "role": "ADMIN",
                                  "status": "INACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.data.email").value("patient.updated@example.com"))
                .andExpect(jsonPath("$.data.role").value("PATIENT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        entityManager.clear();
        User updatedUser = userRepository.findById(patientUser.getId()).orElseThrow();
        Patient updatedPatient = patientRepository.findByUserId(patientUser.getId()).orElseThrow();
        assertThat(updatedUser.getFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(updatedUser.getRole()).isEqualTo(Role.PATIENT);
        assertThat(updatedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(updatedPatient.getFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(updatedPatient.getUser().getId()).isEqualTo(patientUser.getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRejectDuplicateEmailAndRollbackWhenPatientProfileIsMissing() throws Exception {
        String marker = "admin-user-rollback-" + UUID.randomUUID();
        User admin = saveUser(marker + "-admin@example.com", "Quản trị viên",
                Role.ADMIN, UserStatus.ACTIVE);
        User existing = saveUser(marker + "-existing@example.com", "Đã tồn tại",
                Role.STAFF, UserStatus.ACTIVE);
        User patientWithoutProfile = saveUser(marker + "-patient@example.com", "Tên ban đầu",
                Role.PATIENT, UserStatus.ACTIVE);
        String originalEmail = patientWithoutProfile.getEmail();
        try {
            assertThatThrownBy(() -> adminUserService.updateUser(
                    admin.getId(),
                    patientWithoutProfile.getId(),
                    new UpdateAdminUserRequest("Tên mới", existing.getEmail())
            )).isInstanceOf(BusinessException.class);

            assertThatThrownBy(() -> adminUserService.updateUser(
                    admin.getId(),
                    patientWithoutProfile.getId(),
                    new UpdateAdminUserRequest(
                            "Tên phải rollback",
                            marker + "-new@example.com"
                    )
            )).isInstanceOf(NotFoundException.class)
                    .hasMessage("Không tìm thấy hồ sơ bệnh nhân");

            User unchanged = userRepository.findById(patientWithoutProfile.getId()).orElseThrow();
            assertThat(unchanged.getFullName()).isEqualTo("Tên ban đầu");
            assertThat(unchanged.getEmail()).isEqualTo(originalEmail);
        } finally {
            userRepository.deleteAllById(List.of(
                    patientWithoutProfile.getId(),
                    existing.getId(),
                    admin.getId()
            ));
        }
    }

    private User saveUser(String email, String fullName, Role role, UserStatus status) {
        User user = new User();
        user.setEmail(email.toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName(fullName);
        user.setRole(role);
        user.setStatus(status);
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
