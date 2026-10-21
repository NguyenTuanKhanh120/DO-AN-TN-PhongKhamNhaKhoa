package com.dentalclinic.medicalrecord;

import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.service.AuthSessionService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.medicalrecord.controller.PatientMedicalRecordController;
import com.dentalclinic.medicalrecord.dto.PatientMedicalRecordResponse;
import com.dentalclinic.medicalrecord.dto.PrescriptionItem;
import com.dentalclinic.medicalrecord.service.PatientMedicalHistoryService;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PatientMedicalRecordController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class PatientMedicalRecordControllerSecurityTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientMedicalHistoryService patientMedicalHistoryService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private AuthSessionService authSessionService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "patient-history-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/patient/medical-records"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldRejectDoctor() throws Exception {
        mockMvc.perform(get("/api/patient/medical-records")
                        .with(user("doctor@example.com").roles("DOCTOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldReturnPaginatedHistoryToPatient() throws Exception {
        UserPrincipal principal = principal();
        PatientMedicalRecordResponse response = response();
        when(patientMedicalHistoryService.getMedicalHistory(principal.getId(), 0, 20))
                .thenReturn(new PageResponse<>(
                        List.of(response),
                        0,
                        20,
                        1,
                        1,
                        true,
                        true,
                        false,
                        false
                ));

        mockMvc.perform(get("/api/patient/medical-records").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].appointmentId")
                        .value(response.appointmentId().toString()))
                .andExpect(jsonPath("$.data.content[0].doctorFullName")
                        .value("Bác sĩ Nguyễn"))
                .andExpect(jsonPath("$.data.content[0].prescription[0].medicationName")
                        .value("Paracetamol 500mg"))
                .andExpect(jsonPath("$.data.content[0].followUpRequired").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(patientMedicalHistoryService).getMedicalHistory(principal.getId(), 0, 20);
    }

    @Test
    void shouldReturnOwnedRecordByAppointmentId() throws Exception {
        UserPrincipal principal = principal();
        PatientMedicalRecordResponse response = response();
        when(patientMedicalHistoryService.getMedicalRecord(
                principal.getId(),
                response.appointmentId()
        )).thenReturn(response);

        mockMvc.perform(get(
                        "/api/patient/medical-records/{appointmentId}",
                        response.appointmentId()
                ).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(response.id().toString()))
                .andExpect(jsonPath("$.data.diagnosis").value("Viêm tủy răng 36"))
                .andExpect(jsonPath("$.data.actualEndAt")
                        .value("2026-10-12T10:45:00+07:00"));
    }

    @Test
    void shouldValidatePagination() throws Exception {
        mockMvc.perform(get("/api/patient/medical-records?page=-1&size=101")
                        .with(user(principal())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private PatientMedicalRecordResponse response() {
        return new PatientMedicalRecordResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Bác sĩ Nguyễn",
                "Nội nha",
                UUID.randomUUID(),
                "Điều trị tủy",
                OffsetDateTime.parse("2026-10-12T10:00:00+07:00"),
                OffsetDateTime.parse("2026-10-12T10:05:00+07:00"),
                OffsetDateTime.parse("2026-10-12T10:45:00+07:00"),
                "Viêm tủy răng 36",
                "Điều trị tủy lần 1",
                "Điều trị tiếp lần 2",
                "Hạn chế nhai bên đang điều trị",
                List.of(new PrescriptionItem(
                        "Paracetamol 500mg",
                        "1 viên",
                        10,
                        "Uống sau ăn khi đau"
                )),
                true,
                LocalDate.of(2026, 10, 20),
                "Tiếp tục điều trị tủy",
                Instant.parse("2026-10-12T03:45:00Z"),
                Instant.parse("2026-10-12T03:45:00Z")
        );
    }

    private UserPrincipal principal() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("patient@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setFullName("Bệnh nhân kiểm thử");
        user.setRole(Role.PATIENT);
        user.setStatus(UserStatus.ACTIVE);
        return UserPrincipal.from(user);
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
