package com.dentalclinic.auth.service;

import com.dentalclinic.auth.dto.AuthResponse;
import com.dentalclinic.auth.dto.LoginRequest;
import com.dentalclinic.auth.dto.RegisterRequest;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.UnauthorizedException;
import com.dentalclinic.patient.entity.Gender;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public AuthResponse registerPatient(RegisterRequest request) {
        validateBcryptPasswordLength(request.password());
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(normalizeFullName(request.fullName()));
        user.setRole(Role.PATIENT);
        user.setStatus(UserStatus.ACTIVE);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Patient patient = new Patient();
        patient.setUser(user);
        patient.setGender(Gender.UNSPECIFIED);
        patientRepository.save(patient);

        return createAuthResponse(UserPrincipal.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        validateBcryptPasswordLength(request.password());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            normalizeEmail(request.email()),
                            request.password()
                    )
            );
        } catch (DisabledException exception) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        } catch (AuthenticationException exception) {
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS);
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return createAuthResponse(principal);
    }

    private AuthResponse createAuthResponse(UserPrincipal principal) {
        return new AuthResponse(
                jwtTokenProvider.generateAccessToken(principal),
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                principal.getId(),
                principal.getEmail(),
                principal.getFullName(),
                principal.getRole()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeFullName(String fullName) {
        return fullName.trim().replaceAll("\\s+", " ");
    }

    private void validateBcryptPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_LONG);
        }
    }
}
