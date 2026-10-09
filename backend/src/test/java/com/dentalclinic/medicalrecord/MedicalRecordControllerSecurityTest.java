package com.dentalclinic.medicalrecord;

import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.medicalrecord.controller.MedicalRecordController;
import com.dentalclinic.medicalrecord.dto.MedicalRecordRequest;
import com.dentalclinic.medicalrecord.dto.MedicalRecordResponse;
import com.dentalclinic.medicalrecord.dto.PrescriptionItem;
import com.dentalclinic.medicalrecord.service.MedicalRecordService;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
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

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MedicalRecordController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class MedicalRecordControllerSecurityTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MedicalRecordService medicalRecordService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "medical-record-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(post(
                        "/api/doctor/appointments/{id}/medical-record",
                        UUID.randomUUID()
                ).contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldRejectPatient() throws Exception {
        mockMvc.perform(post(
                        "/api/doctor/appointments/{id}/medical-record",
                        UUID.randomUUID()
                ).with(user("patient@example.com").roles("PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowDoctorToCreateMedicalRecord() throws Exception {
        UserPrincipal principal = principal();
        UUID appointmentId = UUID.randomUUID();
        MedicalRecordRequest request = request();
        MedicalRecordResponse response = response(appointmentId);
        when(medicalRecordService.create(principal.getId(), appointmentId, request))
                .thenReturn(response);

        mockMvc.perform(post(
                        "/api/doctor/appointments/{id}/medical-record",
                        appointmentId
                ).with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Tạo bệnh án thành công"))
                .andExpect(jsonPath("$.data.appointmentId")
                        .value(appointmentId.toString()))
                .andExpect(jsonPath("$.data.diagnosis").value("Viêm tủy răng 36"))
                .andExpect(jsonPath("$.data.prescription[0].medicationName")
                        .value("Paracetamol 500mg"))
                .andExpect(jsonPath("$.data.followUpRequired").value(true));

        verify(medicalRecordService).create(principal.getId(), appointmentId, request);
    }

    @Test
    void shouldAllowDoctorToUpdateMedicalRecord() throws Exception {
        UserPrincipal principal = principal();
        UUID appointmentId = UUID.randomUUID();
        MedicalRecordRequest request = request();
        when(medicalRecordService.update(principal.getId(), appointmentId, request))
                .thenReturn(response(appointmentId));

        mockMvc.perform(put(
                        "/api/doctor/appointments/{id}/medical-record",
                        appointmentId
                ).with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật bệnh án thành công"))
                .andExpect(jsonPath("$.data.id").exists());

        verify(medicalRecordService).update(principal.getId(), appointmentId, request);
    }

    @Test
    void shouldValidateRequiredAndNestedPrescriptionFields() throws Exception {
        mockMvc.perform(post(
                        "/api/doctor/appointments/{id}/medical-record",
                        UUID.randomUUID()
                ).with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "diagnosis": " ",
                                  "treatment": "",
                                  "prescription": [
                                    {
                                      "medicationName": "",
                                      "dosage": "",
                                      "quantity": 0,
                                      "instructions": ""
                                    },
                                    null
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(8));
    }

    private String validRequestJson() {
        return """
                {
                  "diagnosis": "Viêm tủy răng 36",
                  "treatment": "Điều trị tủy lần 1",
                  "treatmentPlan": "Điều trị tiếp lần 2",
                  "notes": "Hạn chế nhai bên đang điều trị",
                  "prescription": [
                    {
                      "medicationName": "Paracetamol 500mg",
                      "dosage": "1 viên",
                      "quantity": 10,
                      "instructions": "Uống sau ăn khi đau"
                    }
                  ],
                  "followUpRequired": true,
                  "recommendedFollowUpDate": "2026-10-20",
                  "followUpNote": "Tiếp tục điều trị tủy"
                }
                """;
    }

    private MedicalRecordRequest request() {
        return new MedicalRecordRequest(
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
                "Tiếp tục điều trị tủy"
        );
    }

    private MedicalRecordResponse response(UUID appointmentId) {
        return new MedicalRecordResponse(
                UUID.randomUUID(),
                appointmentId,
                "Viêm tủy răng 36",
                "Điều trị tủy lần 1",
                "Điều trị tiếp lần 2",
                "Hạn chế nhai bên đang điều trị",
                request().prescription(),
                true,
                LocalDate.of(2026, 10, 20),
                "Tiếp tục điều trị tủy",
                Instant.parse("2026-10-12T02:15:00Z"),
                Instant.parse("2026-10-12T02:15:00Z")
        );
    }

    private UserPrincipal principal() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("doctor@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setFullName("Bác sĩ kiểm thử");
        user.setRole(Role.DOCTOR);
        user.setStatus(UserStatus.ACTIVE);
        return UserPrincipal.from(user);
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
