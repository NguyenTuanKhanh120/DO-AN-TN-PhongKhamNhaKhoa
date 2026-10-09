package com.dentalclinic.doctor;

import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.repository.DoctorServiceRepository;
import com.dentalclinic.patient.entity.Gender;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("development")
@Transactional
class DoctorServiceMappingPostgreSqlIntegrationTest {

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
    private DentalServiceRepository dentalServiceRepository;

    @Autowired
    private DoctorServiceRepository doctorServiceRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "doctor-service-mapping-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldManageMappingsAndRespectCompositeForeignKeyOnPostgreSql() throws Exception {
        String marker = UUID.randomUUID().toString();
        User adminUser = saveUser("phase09-admin-" + marker + "@example.com", "Quản trị viên", Role.ADMIN);
        User doctorUser = saveUser("phase09-doctor-" + marker + "@example.com", "Bác sĩ Phase 09", Role.DOCTOR);
        User patientUser = saveUser("phase09-patient-" + marker + "@example.com", "Bệnh nhân", Role.PATIENT);
        Doctor doctor = saveDoctor(doctorUser, marker);
        Patient patient = savePatient(patientUser);
        DentalService serviceA = saveService("Dịch vụ A " + marker, "300000.00");
        DentalService serviceB = saveService("Dịch vụ B " + marker, "500000.00");
        String adminToken = login(adminUser.getEmail());
        String doctorToken = login(doctorUser.getEmail());

        Map<String, Object> initialMappingBody = Map.of(
                "serviceIds",
                List.of(serviceB.getId(), serviceA.getId())
        );
        mockMvc.perform(put("/api/admin/doctors/{doctorId}/services", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(initialMappingBody)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(put("/api/admin/doctors/{doctorId}/services", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(initialMappingBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(serviceA.getId().toString()))
                .andExpect(jsonPath("$.data[1].id").value(serviceB.getId().toString()));

        assertThat(doctorServiceRepository.findAllWithServiceByDoctorId(doctor.getId()))
                .extracting(mapping -> mapping.getId().getServiceId())
                .containsExactly(serviceA.getId(), serviceB.getId());

        mockMvc.perform(get("/api/doctors/{doctorId}/services", doctor.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value(serviceA.getName()))
                .andExpect(jsonPath("$.data[1].name").value(serviceB.getName()));

        mockMvc.perform(get("/api/services/{serviceId}/doctors", serviceA.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(doctor.getId().toString()))
                .andExpect(jsonPath("$.data[0].fullName").value(doctorUser.getFullName()));

        Map<String, Object> retainedMappingBody = Map.of("serviceIds", List.of(serviceB.getId()));
        mockMvc.perform(put("/api/admin/doctors/{doctorId}/services", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(retainedMappingBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(serviceB.getId().toString()));

        assertThat(doctorServiceRepository.existsById(
                new DoctorServiceId(doctor.getId(), serviceA.getId())
        )).isFalse();
        assertThat(doctorServiceRepository.existsById(
                new DoctorServiceId(doctor.getId(), serviceB.getId())
        )).isTrue();

        Map<String, Object> missingServiceBody = Map.of(
                "serviceIds",
                List.of(serviceB.getId(), UUID.randomUUID())
        );
        mockMvc.perform(put("/api/admin/doctors/{doctorId}/services", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(missingServiceBody)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(doctorServiceRepository.findAllWithServiceByDoctorId(doctor.getId()))
                .extracting(mapping -> mapping.getId().getServiceId())
                .containsExactly(serviceB.getId());

        Map<String, Object> duplicateServiceBody = Map.of(
                "serviceIds",
                List.of(serviceB.getId(), serviceB.getId())
        );
        mockMvc.perform(put("/api/admin/doctors/{doctorId}/services", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(duplicateServiceBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(put("/api/admin/doctors/{doctorId}/services", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(retainedMappingBody)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        jdbcTemplate.update(
                """
                        insert into appointments (
                            patient_id,
                            doctor_id,
                            service_id,
                            scheduled_start_at,
                            status
                        ) values (?, ?, ?, cast(? as timestamptz), cast(? as appointment_status_enum))
                        """,
                patient.getId(),
                doctor.getId(),
                serviceB.getId(),
                "2030-01-01T01:00:00Z",
                "BOOKED"
        );

        mockMvc.perform(put("/api/admin/doctors/{doctorId}/services", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(retainedMappingBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(serviceB.getId().toString()));

        mockMvc.perform(put("/api/admin/doctors/{doctorId}/services", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceIds\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
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

    private Doctor saveDoctor(User user, String marker) {
        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setSpecialization("Nha khoa tổng quát");
        doctor.setLicenseNumber("PHASE09-" + marker);
        doctor.setBio("Bác sĩ kiểm thử PHASE 09");
        doctor.setRating(new BigDecimal("4.5"));
        doctor.setExperienceYears(8);
        doctor.setWorkingStatus(DoctorStatus.ACTIVE);
        return doctorRepository.saveAndFlush(doctor);
    }

    private Patient savePatient(User user) {
        Patient patient = new Patient();
        patient.setUser(user);
        patient.setGender(Gender.UNSPECIFIED);
        return patientRepository.saveAndFlush(patient);
    }

    private DentalService saveService(String name, String price) {
        DentalService dentalService = new DentalService();
        dentalService.setName(name);
        dentalService.setDescription("Dịch vụ kiểm thử PHASE 09");
        dentalService.setPrice(new BigDecimal(price));
        dentalService.setActive(true);
        return dentalServiceRepository.saveAndFlush(dentalService);
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
