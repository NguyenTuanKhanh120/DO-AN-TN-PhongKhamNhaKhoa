package com.dentalclinic.patient;

import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.service.AuthSessionService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.patient.controller.PatientController;
import com.dentalclinic.patient.dto.PatientProfileResponse;
import com.dentalclinic.patient.dto.UpdatePatientProfileRequest;
import com.dentalclinic.patient.entity.Gender;
import com.dentalclinic.patient.service.PatientService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PatientController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class PatientControllerSecurityTest {

    private static final String TEST_JWT_SECRET = generateSecret();

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
        registry.add("security.jwt.issuer", () -> "patient-controller-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 60);
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/patients/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldRejectDoctorRole() throws Exception {
        mockMvc.perform(get("/api/patients/me").with(user("doctor@example.com").roles("DOCTOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldGetProfileForAuthenticatedPatient() throws Exception {
        UserPrincipal principal = principal();
        when(patientService.getMyProfile(principal.getId())).thenReturn(profile(principal));

        mockMvc.perform(get("/api/patients/me").with(authentication(authenticated(principal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(principal.getId().toString()))
                .andExpect(jsonPath("$.data.email").value(principal.getEmail()));

        verify(patientService).getMyProfile(principal.getId());
    }

    @Test
    void shouldUpdateProfileForAuthenticatedPatient() throws Exception {
        UserPrincipal principal = principal();
        when(patientService.updateMyProfile(eq(principal.getId()), any(UpdatePatientProfileRequest.class)))
                .thenReturn(profile(principal));

        mockMvc.perform(put("/api/patients/me")
                        .with(authentication(authenticated(principal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Nguyễn Văn An",
                                  "dateOfBirth": "2000-05-20",
                                  "gender": "MALE",
                                  "phone": "0901234567",
                                  "address": "TP.HCM",
                                  "medicalHistory": "Không",
                                  "allergies": "Không",
                                  "userId": "00000000-0000-0000-0000-000000000000",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(patientService).updateMyProfile(eq(principal.getId()), any(UpdatePatientProfileRequest.class));
    }

    @Test
    void shouldValidateProfileUpdate() throws Exception {
        UserPrincipal principal = principal();

        mockMvc.perform(put("/api/patients/me")
                        .with(authentication(authenticated(principal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"gender\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private UsernamePasswordAuthenticationToken authenticated(UserPrincipal principal) {
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                "token",
                principal.getAuthorities()
        );
    }

    private UserPrincipal principal() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("patient@example.com");
        user.setPasswordHash("hash");
        user.setFullName("Nguyễn Văn An");
        user.setRole(Role.PATIENT);
        user.setStatus(UserStatus.ACTIVE);
        return UserPrincipal.from(user);
    }

    private PatientProfileResponse profile(UserPrincipal principal) {
        return new PatientProfileResponse(
                UUID.randomUUID(),
                principal.getId(),
                principal.getEmail(),
                principal.getFullName(),
                null,
                Gender.UNSPECIFIED,
                null,
                null,
                null,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
