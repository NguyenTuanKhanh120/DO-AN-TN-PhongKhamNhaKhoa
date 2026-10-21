package com.dentalclinic.auth;

import com.dentalclinic.auth.dto.AuthResponse;
import com.dentalclinic.auth.dto.LoginRequest;
import com.dentalclinic.auth.dto.RegisterRequest;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.auth.service.AuthSessionService;
import com.dentalclinic.auth.service.AuthService;
import com.dentalclinic.auth.service.SessionAuthenticationResult;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.patient.entity.Gender;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private AuthSessionService authSessionService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(
                userRepository,
                patientRepository,
                passwordEncoder,
                authenticationManager,
                authSessionService
        );
    }

    @Test
    void shouldRegisterPatientWithNormalizedDataAndHashedPassword() {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsByEmail("patient@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });
        when(authSessionService.startSession(any(UserPrincipal.class)))
                .thenAnswer(invocation -> authenticationResult(invocation.getArgument(0)));

        AuthResponse response = authService.registerPatient(new RegisterRequest(
                " Patient@Example.com ",
                "StrongPassword123!",
                "  Nguyễn   Văn   An  "
        )).response();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("patient@example.com");
        assertThat(savedUser.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(savedUser.getRole()).isEqualTo(Role.PATIENT);
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("StrongPassword123!");
        assertThat(passwordEncoder.matches("StrongPassword123!", savedUser.getPasswordHash())).isTrue();

        ArgumentCaptor<Patient> patientCaptor = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(patientCaptor.capture());
        assertThat(patientCaptor.getValue().getUser()).isSameAs(savedUser);
        assertThat(patientCaptor.getValue().getGender()).isEqualTo(Gender.UNSPECIFIED);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(900L);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.role()).isEqualTo(Role.PATIENT);
    }

    @Test
    void shouldRejectAnExistingEmailBeforeWriting() {
        when(userRepository.existsByEmail("patient@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerPatient(new RegisterRequest(
                "patient@example.com",
                "StrongPassword123!",
                "Nguyễn Văn An"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    void shouldRejectPasswordLongerThanBcryptUtf8Limit() {
        String multibytePassword = "😀".repeat(20);

        assertThatThrownBy(() -> authService.registerPatient(new RegisterRequest(
                "patient@example.com",
                multibytePassword,
                "Nguyễn Văn An"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_TOO_LONG);

        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void shouldTranslateConcurrentEmailConstraintConflict() {
        when(userRepository.existsByEmail("patient@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> authService.registerPatient(new RegisterRequest(
                "patient@example.com",
                "StrongPassword123!",
                "Nguyễn Văn An"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void shouldLoginAndIssueAccessToken() {
        UserPrincipal principal = principal(UserStatus.ACTIVE);
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(authSessionService.startSession(principal)).thenReturn(authenticationResult(principal));

        AuthResponse response = authService.login(new LoginRequest(
                " Patient@Example.com ",
                "StrongPassword123!"
        )).response();

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.email()).isEqualTo("patient@example.com");
        assertThat(response.role()).isEqualTo(Role.PATIENT);
    }

    @Test
    void shouldReturnGenericErrorForInvalidCredentials() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest(
                "patient@example.com",
                "wrong-password"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void shouldRejectInactiveAccount() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new DisabledException("disabled"));

        assertThatThrownBy(() -> authService.login(new LoginRequest(
                "patient@example.com",
                "StrongPassword123!"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_INACTIVE);
    }

    private UserPrincipal principal(UserStatus status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("patient@example.com");
        user.setPasswordHash(passwordEncoder.encode("StrongPassword123!"));
        user.setFullName("Nguyễn Văn An");
        user.setRole(Role.PATIENT);
        user.setStatus(status);
        return UserPrincipal.from(user);
    }

    private SessionAuthenticationResult authenticationResult(UserPrincipal principal) {
        return new SessionAuthenticationResult(
                new AuthResponse(
                        "access-token",
                        900,
                        principal.getId(),
                        principal.getEmail(),
                        principal.getFullName(),
                        principal.getRole()
                ),
                "refresh-token",
                "csrf-token",
                604800
        );
    }
}
