package com.dentalclinic.doctor;

import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
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
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
class DoctorManagementPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final String PASSWORD = "StrongPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private DoctorScheduleRepository doctorScheduleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "doctor-management-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldManageDoctorAccountAndPublicProfileOnPostgreSql() throws Exception {
        String marker = UUID.randomUUID().toString();
        User admin = saveUser("phase07-admin-" + marker + "@example.com", "Quản trị viên", Role.ADMIN);
        User patient = saveUser("phase07-patient-" + marker + "@example.com", "Bệnh nhân", Role.PATIENT);
        String adminToken = login(admin.getEmail(), PASSWORD);
        String patientToken = login(patient.getEmail(), PASSWORD);

        String doctorEmail = "phase07-doctor-" + marker + "@example.com";
        String licenseNumber = "PHASE07-" + marker;
        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("email", doctorEmail.toUpperCase());
        createBody.put("password", PASSWORD);
        createBody.put("fullName", "  Bác sĩ   Nguyễn Văn An  ");
        createBody.put("specialization", "  Nha khoa tổng quát  ");
        createBody.put("licenseNumber", licenseNumber);
        createBody.put("bio", "  Hồ sơ bác sĩ PHASE 07  ");
        createBody.put("experienceYears", 8);

        mockMvc.perform(post("/api/admin/doctors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(patientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createBody)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        MvcResult created = mockMvc.perform(post("/api/admin/doctors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(doctorEmail))
                .andExpect(jsonPath("$.data.fullName").value("Bác sĩ Nguyễn Văn An"))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.workingStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.rating").value(0.0))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn();

        JsonNode createdBody = objectMapper.readTree(created.getResponse().getContentAsByteArray());
        UUID doctorId = UUID.fromString(createdBody.path("data").path("id").asText());
        UUID doctorUserId = UUID.fromString(createdBody.path("data").path("userId").asText());

        User doctorUser = userRepository.findById(doctorUserId).orElseThrow();
        Doctor doctor = doctorRepository.findById(doctorId).orElseThrow();
        assertThat(doctorUser.getRole()).isEqualTo(Role.DOCTOR);
        assertThat(doctorUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(doctorUser.getEmail()).isEqualTo(doctorEmail);
        assertThat(doctorUser.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, doctorUser.getPasswordHash())).isTrue();
        assertThat(doctor.getUser().getId()).isEqualTo(doctorUserId);
        assertThat(doctor.getLicenseNumber()).isEqualTo(licenseNumber);
        assertThat(doctor.getRating()).isEqualByComparingTo("0.0");
        assertThat(doctorScheduleRepository
                .findAllByDoctor_IdOrderByDayOfWeekAscSessionAscStartTimeAsc(doctorId))
                .hasSize(12)
                .allSatisfy(schedule -> {
                    assertThat(schedule.getDayOfWeek()).isBetween((short) 1, (short) 6);
                    if (schedule.getSession() == ScheduleSession.MORNING) {
                        assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(8, 0));
                        assertThat(schedule.getEndTime()).isEqualTo(LocalTime.of(11, 0));
                    } else {
                        assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(13, 0));
                        assertThat(schedule.getEndTime()).isEqualTo(LocalTime.of(17, 0));
                    }
                });
        assertThat(login(doctorEmail, PASSWORD)).isNotBlank();

        mockMvc.perform(get("/api/doctors")
                        .param("search", marker.toUpperCase())
                        .param("specialization", "NHA KHOA TỔNG QUÁT")
                        .param("workingStatus", "ACTIVE")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(doctorId.toString()))
                .andExpect(jsonPath("$.data.content[0].fullName").value("Bác sĩ Nguyễn Văn An"))
                .andExpect(jsonPath("$.data.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].accountStatus").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist());

        mockMvc.perform(get("/api/doctors/{id}", doctorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(doctorId.toString()))
                .andExpect(jsonPath("$.data.licenseNumber").value(licenseNumber));

        Map<String, Object> updateBody = new LinkedHashMap<>();
        updateBody.put("fullName", "  Bác sĩ   Trần Thị Bình  ");
        updateBody.put("specialization", "  Chỉnh nha  ");
        updateBody.put("licenseNumber", licenseNumber + "-UPDATED");
        updateBody.put("bio", "  Tiểu sử đã cập nhật  ");
        updateBody.put("rating", new BigDecimal("4.5"));
        updateBody.put("experienceYears", 12);

        mockMvc.perform(put("/api/admin/doctors/{id}", doctorId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Bác sĩ Trần Thị Bình"))
                .andExpect(jsonPath("$.data.specialization").value("Chỉnh nha"))
                .andExpect(jsonPath("$.data.rating").value(4.5));

        Doctor updatedDoctor = doctorRepository.findById(doctorId).orElseThrow();
        assertThat(updatedDoctor.getUser().getFullName()).isEqualTo("Bác sĩ Trần Thị Bình");
        assertThat(updatedDoctor.getSpecialization()).isEqualTo("Chỉnh nha");
        assertThat(updatedDoctor.getRating()).isEqualByComparingTo("4.5");
        assertThat(updatedDoctor.getExperienceYears()).isEqualTo(12);

        mockMvc.perform(patch("/api/admin/doctors/{id}/status", doctorId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ON_LEAVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workingStatus").value("ON_LEAVE"))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"));

        assertThat(doctorRepository.findById(doctorId).orElseThrow().getWorkingStatus())
                .isEqualTo(DoctorStatus.ON_LEAVE);
        assertThat(userRepository.findById(doctorUserId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);

        createBody.put("email", "phase07-license-conflict-" + marker + "@example.com");
        createBody.put("licenseNumber", licenseNumber + "-UPDATED");
        mockMvc.perform(post("/api/admin/doctors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LICENSE_NUMBER_ALREADY_EXISTS"));
        assertThat(userRepository.existsByEmail(createBody.get("email").toString())).isFalse();

        doctorUser.setStatus(UserStatus.INACTIVE);
        userRepository.saveAndFlush(doctorUser);

        mockMvc.perform(get("/api/doctors/{id}", doctorId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(put("/api/admin/doctors/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(updateBody)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
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
