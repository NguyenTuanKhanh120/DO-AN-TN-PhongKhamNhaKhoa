package com.dentalclinic.patient;

import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.patient.entity.Gender;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.user.entity.Role;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.time.LocalDate;
import java.util.LinkedHashMap;
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
class PatientProfilePostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();
    private static final String PASSWORD = "StrongPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ClinicTimeProvider timeProvider;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "patient-profile-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldReadAndUpdateOnlyAuthenticatedPatientProfile() throws Exception {
        RegisteredPatient first = registerPatient("first", "Bệnh nhân Thứ Nhất");
        RegisteredPatient second = registerPatient("second", "Bệnh nhân Thứ Hai");

        mockMvc.perform(get("/api/patients/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(first.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(first.userId().toString()))
                .andExpect(jsonPath("$.data.email").value(first.email()))
                .andExpect(jsonPath("$.data.fullName").value("Bệnh nhân Thứ Nhất"))
                .andExpect(jsonPath("$.data.gender").value("UNSPECIFIED"));

        Map<String, Object> updateBody = new LinkedHashMap<>();
        updateBody.put("fullName", "  Nguyễn   Thị   Lan  ");
        updateBody.put("dateOfBirth", "1999-04-15");
        updateBody.put("gender", "FEMALE");
        updateBody.put("phone", " 0901234567 ");
        updateBody.put("address", "  Thành phố Hồ Chí Minh ");
        updateBody.put("medicalHistory", "Viêm nướu");
        updateBody.put("allergies", "Penicillin");
        updateBody.put("userId", second.userId().toString());
        updateBody.put("email", "attacker@example.com");
        updateBody.put("role", "ADMIN");

        mockMvc.perform(put("/api/patients/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(first.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(first.userId().toString()))
                .andExpect(jsonPath("$.data.email").value(first.email()))
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Thị Lan"))
                .andExpect(jsonPath("$.data.dateOfBirth").value("1999-04-15"))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"));

        Patient firstPatient = patientRepository.findByUserId(first.userId()).orElseThrow();
        assertThat(firstPatient.getUser().getEmail()).isEqualTo(first.email());
        assertThat(firstPatient.getUser().getRole()).isEqualTo(Role.PATIENT);
        assertThat(firstPatient.getUser().getFullName()).isEqualTo("Nguyễn Thị Lan");
        assertThat(firstPatient.getDateOfBirth()).isEqualTo(LocalDate.of(1999, 4, 15));
        assertThat(firstPatient.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(firstPatient.getPhone()).isEqualTo("0901234567");
        assertThat(firstPatient.getAddress()).isEqualTo("Thành phố Hồ Chí Minh");
        assertThat(firstPatient.getMedicalHistory()).isEqualTo("Viêm nướu");
        assertThat(firstPatient.getAllergies()).isEqualTo("Penicillin");

        mockMvc.perform(get("/api/patients/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(second.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(second.userId().toString()))
                .andExpect(jsonPath("$.data.fullName").value("Bệnh nhân Thứ Hai"))
                .andExpect(jsonPath("$.data.dateOfBirth").doesNotExist());

        String futureDate = timeProvider.today().plusDays(1).toString();
        updateBody.put("dateOfBirth", futureDate);

        mockMvc.perform(put("/api/patients/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(first.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(updateBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        Patient unchangedAfterRejectedUpdate = patientRepository.findByUserId(first.userId()).orElseThrow();
        assertThat(unchangedAfterRejectedUpdate.getDateOfBirth()).isEqualTo(LocalDate.of(1999, 4, 15));
    }

    private RegisteredPatient registerPatient(String prefix, String fullName) throws Exception {
        String email = prefix + "-phase05-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "email", email,
                                "password", PASSWORD,
                                "fullName", fullName
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return new RegisteredPatient(
                UUID.fromString(body.path("data").path("userId").asText()),
                email,
                body.path("data").path("accessToken").asText()
        );
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }

    private record RegisteredPatient(UUID userId, String email, String accessToken) {
    }
}
