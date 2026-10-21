package com.dentalclinic.auth;

import com.dentalclinic.auth.controller.AuthController;
import com.dentalclinic.auth.dto.AuthResponse;
import com.dentalclinic.auth.dto.AuthSessionResponse;
import com.dentalclinic.auth.dto.LoginRequest;
import com.dentalclinic.auth.dto.RegisterRequest;
import com.dentalclinic.auth.entity.AuthSessionStatus;
import com.dentalclinic.auth.exception.SessionException;
import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.security.JwtAuthenticationFilter;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.RestSecurityExceptionHandler;
import com.dentalclinic.auth.service.AuthService;
import com.dentalclinic.auth.service.AuthCookieService;
import com.dentalclinic.auth.service.AuthSessionService;
import com.dentalclinic.auth.service.SessionAuthenticationResult;
import com.dentalclinic.common.exception.GlobalExceptionHandler;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.config.SecurityConfig;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.UUID;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestSecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class AuthControllerSecurityTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthSessionService authSessionService;

    @MockitoBean
    private AuthCookieService authCookieService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "dental-clinic-web-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 15);
    }

    @Test
    void shouldAllowAnonymousPatientRegistration() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.registerPatient(any(RegisterRequest.class)))
                .thenReturn(authenticationResult(userId));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "patient@example.com",
                                  "password": "StrongPassword123!",
                                  "fullName": "Nguyễn Văn An",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.role").value("PATIENT"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    void shouldAllowAnonymousLogin() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(authenticationResult(UUID.randomUUID()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "patient@example.com",
                                  "password": "StrongPassword123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    void shouldAllowCookieBasedRefreshWithoutReturningRefreshTokenInJson() throws Exception {
        SessionAuthenticationResult authentication = authenticationResult(UUID.randomUUID());
        when(authCookieService.readRefreshToken(any())).thenReturn("refresh-token");
        when(authCookieService.readCsrfCookie(any())).thenReturn("csrf-token");
        when(authCookieService.readCsrfHeader(any())).thenReturn("csrf-token");
        when(authSessionService.refresh("refresh-token", "csrf-token", "csrf-token"))
                .thenReturn(authentication);

        mockMvc.perform(post("/api/auth/refresh")
                        .header(AuthCookieService.CSRF_HEADER, "csrf-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());

        verify(authCookieService).writeSessionCookies(any(), any());
    }

    @Test
    void shouldRequireBearerAuthenticationForSessionInspection() throws Exception {
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldNotClearSessionCookiesWhenLogoutFailsCsrfValidation() throws Exception {
        when(authCookieService.readRefreshToken(any())).thenReturn("refresh-token");
        when(authCookieService.readCsrfCookie(any())).thenReturn("csrf-token");
        when(authCookieService.readCsrfHeader(any())).thenReturn("wrong-token");
        org.mockito.Mockito.doThrow(new SessionException(ErrorCode.CSRF_VALIDATION_FAILED))
                .when(authSessionService)
                .logout("refresh-token", "csrf-token", "wrong-token");

        mockMvc.perform(post("/api/auth/logout")
                        .header(AuthCookieService.CSRF_HEADER, "wrong-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_VALIDATION_FAILED"));

        verify(authCookieService, never()).clearSessionCookies(any());
    }

    @Test
    void shouldReturnCurrentServerSideSessionToAuthenticatedUser() throws Exception {
        com.dentalclinic.auth.security.UserPrincipal principal = principal();
        AuthSessionResponse session = new AuthSessionResponse(
                principal.getSessionId(),
                Role.DOCTOR,
                AuthSessionStatus.ACTIVE,
                Instant.parse("2026-10-21T08:00:00Z"),
                Instant.parse("2026-10-21T20:00:00Z"),
                Instant.parse("2026-10-21T08:05:00Z"),
                null,
                1200,
                true
        );
        when(authSessionService.getSession(principal)).thenReturn(session);

        mockMvc.perform(get("/api/auth/session").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.idleTimeoutSeconds").value(1200));
    }

    @Test
    void shouldRejectInvalidRegistrationPayload() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid\",\"password\":\"short\",\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldRequireAuthenticationForProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/patient/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void shouldRejectWrongRoleForPatientEndpoint() throws Exception {
        mockMvc.perform(get("/api/patient/test"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldRejectInvalidBearerToken() throws Exception {
        when(jwtTokenProvider.extractAccessTokenClaims("invalid-token"))
                .thenThrow(new MalformedJwtException("invalid"));

        mockMvc.perform(get("/api/patient/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private AuthResponse authResponse(UUID userId) {
        return new AuthResponse(
                "access-token",
                900,
                userId,
                "patient@example.com",
                "Nguyễn Văn An",
                Role.PATIENT
        );
    }

    private SessionAuthenticationResult authenticationResult(UUID userId) {
        return new SessionAuthenticationResult(
                authResponse(userId),
                "refresh-token",
                "csrf-token",
                604800
        );
    }

    private com.dentalclinic.auth.security.UserPrincipal principal() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("doctor@example.com");
        user.setPasswordHash("encoded-password");
        user.setFullName("Bác sĩ kiểm thử");
        user.setRole(Role.DOCTOR);
        user.setStatus(UserStatus.ACTIVE);
        return com.dentalclinic.auth.security.UserPrincipal.from(user)
                .withSessionId(UUID.randomUUID());
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }
}
