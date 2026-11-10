package com.dentalclinic.patient;

import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.auth.service.AuthSessionService;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.patient.controller.StaffPatientController;
import com.dentalclinic.patient.dto.CreateStaffPatientRequest;
import com.dentalclinic.patient.dto.StaffPatientResponse;
import com.dentalclinic.patient.entity.Gender;
import com.dentalclinic.patient.service.PatientService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaffPatientController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class StaffPatientControllerSecurityTest {

    private static final String TEST_JWT_SECRET = Encoders.BASE64.encode(
            Jwts.SIG.HS256.key().build().getEncoded()
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientService patientService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private AuthSessionService authSessionService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "staff-patient-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldRequireStaffRole() throws Exception {
        mockMvc.perform(get("/api/staff/patients"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/staff/patients")
                        .with(user("patient@example.com").roles("PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowStaffToSearchPatients() throws Exception {
        StaffPatientResponse patient = response();
        PageResponse<StaffPatientResponse> page = new PageResponse<>(
                List.of(patient), 0, 20, 1, 1, true, true, false, false
        );
        when(patientService.searchForStaff("Nguyễn", 0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/staff/patients")
                        .with(user("staff@example.com").roles("STAFF"))
                        .param("search", "Nguyễn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(patient.id().toString()))
                .andExpect(jsonPath("$.data.content[0].fullName").value(patient.fullName()));

        verify(patientService).searchForStaff("Nguyễn", 0, 20);
    }

    @Test
    void shouldAllowStaffToCreatePatientWithoutAccountCredentials() throws Exception {
        StaffPatientResponse patient = response();
        when(patientService.createForStaff(any(CreateStaffPatientRequest.class)))
                .thenReturn(patient);

        mockMvc.perform(post("/api/staff/patients")
                        .with(user("staff@example.com").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Nguyễn Văn Bình",
                                  "phone": "0901234567",
                                  "gender": "MALE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(patient.id().toString()))
                .andExpect(jsonPath("$.data.fullName").value(patient.fullName()));

        verify(patientService).createForStaff(any(CreateStaffPatientRequest.class));
    }

    @Test
    void shouldValidateRequiredPatientIdentity() throws Exception {
        mockMvc.perform(post("/api/staff/patients")
                        .with(user("staff@example.com").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": " ",
                                  "phone": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private StaffPatientResponse response() {
        Instant timestamp = Instant.parse("2026-11-09T01:00:00Z");
        return new StaffPatientResponse(
                UUID.randomUUID(),
                "Nguyễn Văn Bình",
                "0901234567",
                null,
                Gender.MALE,
                null,
                null,
                null,
                timestamp,
                timestamp
        );
    }
}
