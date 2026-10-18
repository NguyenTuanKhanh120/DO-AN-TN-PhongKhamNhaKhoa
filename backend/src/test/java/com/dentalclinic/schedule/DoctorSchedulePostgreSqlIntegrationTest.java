package com.dentalclinic.schedule;

import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.schedule.entity.DoctorSchedule;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("development")
@Transactional
class DoctorSchedulePostgreSqlIntegrationTest {

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
        registry.add("security.jwt.issuer", () -> "doctor-schedule-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldAllowOnlyAdminToManageWeeklyScheduleOnPostgreSql() throws Exception {
        String marker = UUID.randomUUID().toString();
        User doctorUser = saveUser(
                "phase10-doctor-" + marker + "@example.com",
                "Bác sĩ Lịch Tuần",
                Role.DOCTOR
        );
        User otherDoctorUser = saveUser(
                "phase10-other-" + marker + "@example.com",
                "Bác sĩ Khác",
                Role.DOCTOR
        );
        User adminUser = saveUser(
                "phase10-admin-" + marker + "@example.com",
                "Quản trị viên",
                Role.ADMIN
        );
        User patientUser = saveUser(
                "phase10-patient-" + marker + "@example.com",
                "Bệnh nhân",
                Role.PATIENT
        );
        Doctor doctor = saveDoctor(doctorUser, "PHASE10-A-" + marker);
        Doctor otherDoctor = saveDoctor(otherDoctorUser, "PHASE10-B-" + marker);
        String doctorToken = login(doctorUser.getEmail());
        String adminToken = login(adminUser.getEmail());
        String patientToken = login(patientUser.getEmail());

        mockMvc.perform(get("/api/doctors/{doctorId}/schedule", doctor.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        Map<String, Object> morningBody = scheduleBody(1, "MORNING", "07:00", "12:00");
        mockMvc.perform(post("/api/admin/doctors/{doctorId}/schedules", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(patientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(morningBody)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/admin/doctors/{doctorId}/schedules", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(morningBody)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/doctor/schedules")
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(morningBody)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        MvcResult morningCreated = mockMvc.perform(post(
                        "/api/admin/doctors/{doctorId}/schedules",
                        doctor.getId()
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(morningBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.doctorId").value(doctor.getId().toString()))
                .andExpect(jsonPath("$.data.dayOfWeek").value(1))
                .andExpect(jsonPath("$.data.session").value("MORNING"))
                .andExpect(jsonPath("$.data.startTime").value("07:00:00"))
                .andExpect(jsonPath("$.data.endTime").value("12:00:00"))
                .andReturn();
        UUID morningId = responseId(morningCreated);

        DoctorSchedule persistedMorning = doctorScheduleRepository.findById(morningId).orElseThrow();
        assertThat(persistedMorning.getDoctor().getId()).isEqualTo(doctor.getId());
        assertThat(persistedMorning.getDayOfWeek()).isEqualTo((short) 1);
        assertThat(persistedMorning.getSession()).isEqualTo(ScheduleSession.MORNING);
        assertThat(persistedMorning.getStartTime()).isEqualTo(LocalTime.of(7, 0));

        mockMvc.perform(post("/api/admin/doctors/{doctorId}/schedules", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(morningBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_ALREADY_EXISTS"));

        Map<String, Object> invalidTimeBody = scheduleBody(2, "MORNING", "12:00", "07:00");
        mockMvc.perform(post("/api/admin/doctors/{doctorId}/schedules", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(invalidTimeBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        Map<String, Object> afternoonBody = scheduleBody(1, "AFTERNOON", "13:00", "18:00");
        MvcResult afternoonCreated = mockMvc.perform(post(
                        "/api/admin/doctors/{doctorId}/schedules",
                        doctor.getId()
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(afternoonBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.session").value("AFTERNOON"))
                .andReturn();
        UUID afternoonId = responseId(afternoonCreated);

        mockMvc.perform(get("/api/doctor/schedules")
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(morningId.toString()))
                .andExpect(jsonPath("$.data[1].id").value(afternoonId.toString()));

        mockMvc.perform(get("/api/admin/doctors/{doctorId}/schedules", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/doctors/{doctorId}/schedule", doctor.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].session").value("MORNING"))
                .andExpect(jsonPath("$.data[1].session").value("AFTERNOON"));

        Map<String, Object> movedMorningBody = scheduleBody(2, "MORNING", "08:00", "12:00");
        mockMvc.perform(put(
                        "/api/admin/doctors/{doctorId}/schedules/{scheduleId}",
                        otherDoctor.getId(),
                        morningId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(movedMorningBody)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(put(
                        "/api/admin/doctors/{doctorId}/schedules/{scheduleId}",
                        doctor.getId(),
                        morningId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(movedMorningBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dayOfWeek").value(2))
                .andExpect(jsonPath("$.data.startTime").value("08:00:00"));

        Map<String, Object> conflictingUpdate = scheduleBody(1, "AFTERNOON", "14:00", "17:00");
        mockMvc.perform(put(
                        "/api/admin/doctors/{doctorId}/schedules/{scheduleId}",
                        doctor.getId(),
                        morningId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(conflictingUpdate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_ALREADY_EXISTS"));

        DoctorSchedule unchangedMorning = doctorScheduleRepository.findById(morningId).orElseThrow();
        assertThat(unchangedMorning.getDayOfWeek()).isEqualTo((short) 2);
        assertThat(unchangedMorning.getSession()).isEqualTo(ScheduleSession.MORNING);
        assertThat(unchangedMorning.getStartTime()).isEqualTo(LocalTime.of(8, 0));

        mockMvc.perform(delete(
                        "/api/admin/doctors/{doctorId}/schedules/{scheduleId}",
                        otherDoctor.getId(),
                        afternoonId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(doctorScheduleRepository.existsById(afternoonId)).isTrue();

        mockMvc.perform(delete(
                        "/api/admin/doctors/{doctorId}/schedules/{scheduleId}",
                        doctor.getId(),
                        afternoonId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(doctorScheduleRepository.existsById(afternoonId)).isFalse();

        List<DoctorSchedule> remaining = doctorScheduleRepository
                .findAllByDoctor_IdOrderByDayOfWeekAscSessionAscStartTimeAsc(doctor.getId());
        assertThat(remaining).singleElement().extracting(DoctorSchedule::getId).isEqualTo(morningId);

        doctorUser.setStatus(UserStatus.INACTIVE);
        userRepository.saveAndFlush(doctorUser);
        mockMvc.perform(get("/api/doctors/{doctorId}/schedule", doctor.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private User saveUser(String email, String fullName, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName(fullName);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private Doctor saveDoctor(User user, String licenseNumber) {
        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setSpecialization("Nha khoa tổng quát");
        doctor.setLicenseNumber(licenseNumber);
        doctor.setBio("Bác sĩ kiểm thử PHASE 10");
        doctor.setRating(new BigDecimal("4.5"));
        doctor.setExperienceYears(8);
        doctor.setWorkingStatus(DoctorStatus.ACTIVE);
        return doctorRepository.saveAndFlush(doctor);
    }

    private Map<String, Object> scheduleBody(
            int dayOfWeek,
            String session,
            String startTime,
            String endTime
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dayOfWeek", dayOfWeek);
        body.put("session", session);
        body.put("startTime", startTime);
        body.put("endTime", endTime);
        return body;
    }

    private UUID responseId(MvcResult result) throws Exception {
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsByteArray())
                        .path("data")
                        .path("id")
                        .asText()
        );
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
