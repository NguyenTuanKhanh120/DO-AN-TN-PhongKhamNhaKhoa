package com.dentalclinic.auth;

import com.dentalclinic.auth.entity.AuthSession;
import com.dentalclinic.auth.entity.AuthSessionStatus;
import com.dentalclinic.auth.exception.SessionException;
import com.dentalclinic.auth.repository.AuthSessionRepository;
import com.dentalclinic.auth.repository.RefreshTokenRepository;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.auth.service.AuthSessionService;
import com.dentalclinic.auth.service.SecureTokenService;
import com.dentalclinic.auth.service.SessionAuthenticationResult;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("development")
class AuthSessionPostgreSqlIntegrationTest {

    private static final String TEST_JWT_SECRET = generateSecret();

    @Autowired
    private AuthSessionService authSessionService;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private SecureTokenService secureTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdUserIds = new ArrayList<>();

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.jwt.issuer", () -> "auth-session-integration-test");
        registry.add("security.jwt.access-token-expiration-minutes", () -> 15);
    }

    @AfterEach
    void cleanUp() {
        createdUserIds.forEach(userRepository::deleteById);
        createdUserIds.clear();
    }

    @Test
    void shouldPersistConfiguredPoliciesForAllFourRoles() {
        assertPolicy(Role.ADMIN, Duration.ofHours(8), 15 * 60);
        assertPolicy(Role.DOCTOR, Duration.ofHours(12), 20 * 60);
        assertPolicy(Role.STAFF, Duration.ofHours(12), 20 * 60);
        assertPolicy(Role.PATIENT, Duration.ofDays(7), null);
    }

    @Test
    void shouldRotateThenDetectReuseAndRevokeTheWholeSession() {
        User user = createUser(Role.DOCTOR);
        SessionAuthenticationResult login = authSessionService.startSession(UserPrincipal.from(user));
        UUID sessionId = sessionIdFor(login.refreshToken());
        long initialTokenCount = refreshTokenCount(sessionId);

        SessionAuthenticationResult refreshed = authSessionService.refresh(
                login.refreshToken(),
                login.csrfToken(),
                login.csrfToken()
        );

        assertThat(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());
        assertThat(refreshed.csrfToken()).isEqualTo(login.csrfToken());
        assertThat(refreshTokenCount(sessionId)).isEqualTo(initialTokenCount + 1);
        assertThat(currentRefreshTokenCount(sessionId)).isEqualTo(1);

        assertThatThrownBy(() -> authSessionService.refresh(
                login.refreshToken(),
                login.csrfToken(),
                login.csrfToken()
        )).isInstanceOfSatisfying(SessionException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_REUSED));

        AuthSession revoked = authSessionRepository.findById(sessionId).orElseThrow();
        assertThat(revoked.getStatus()).isEqualTo(AuthSessionStatus.REVOKED);
        assertThat(revoked.getRevocationReason()).isEqualTo("REFRESH_TOKEN_REUSE");
        assertThat(currentRefreshTokenCount(sessionId)).isZero();
    }

    @Test
    void shouldSerializeConcurrentRefreshAndLeaveNoValidTokenAfterReuseDetection() throws Exception {
        User user = createUser(Role.ADMIN);
        SessionAuthenticationResult login = authSessionService.startSession(UserPrincipal.from(user));
        UUID sessionId = sessionIdFor(login.refreshToken());
        CountDownLatch startGate = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<RefreshOutcome> first = executor.submit(
                    () -> refreshAfterGate(login, startGate)
            );
            Future<RefreshOutcome> second = executor.submit(
                    () -> refreshAfterGate(login, startGate)
            );
            startGate.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .extracting(RefreshOutcome::code)
                    .containsExactlyInAnyOrder(ErrorCode.SUCCESS, ErrorCode.REFRESH_TOKEN_REUSED);
        }

        assertThat(authSessionRepository.findById(sessionId).orElseThrow().getStatus())
                .isEqualTo(AuthSessionStatus.REVOKED);
        assertThat(currentRefreshTokenCount(sessionId)).isZero();
    }

    @Test
    void shouldRevokeSessionOnLogoutAndWhenAccountBecomesInactive() {
        User patient = createUser(Role.PATIENT);
        SessionAuthenticationResult patientLogin = authSessionService.startSession(
                UserPrincipal.from(patient)
        );
        UUID patientSessionId = sessionIdFor(patientLogin.refreshToken());
        authSessionService.logout(
                patientLogin.refreshToken(),
                patientLogin.csrfToken(),
                patientLogin.csrfToken()
        );
        assertThat(authSessionRepository.findById(patientSessionId).orElseThrow().getStatus())
                .isEqualTo(AuthSessionStatus.REVOKED);

        User staff = createUser(Role.STAFF);
        SessionAuthenticationResult staffLogin = authSessionService.startSession(UserPrincipal.from(staff));
        UUID staffSessionId = sessionIdFor(staffLogin.refreshToken());
        staff.setStatus(UserStatus.INACTIVE);
        userRepository.saveAndFlush(staff);

        assertThatThrownBy(() -> authSessionService.refresh(
                staffLogin.refreshToken(),
                staffLogin.csrfToken(),
                staffLogin.csrfToken()
        )).isInstanceOfSatisfying(SessionException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_INACTIVE));
        assertThat(authSessionRepository.findById(staffSessionId).orElseThrow().getStatus())
                .isEqualTo(AuthSessionStatus.REVOKED);
    }

    private void assertPolicy(Role role, Duration lifetime, Integer idleTimeoutSeconds) {
        User user = createUser(role);
        SessionAuthenticationResult result = authSessionService.startSession(UserPrincipal.from(user));
        AuthSession session = authSessionRepository.findById(
                sessionIdFor(result.refreshToken())
        ).orElseThrow();
        assertThat(Duration.between(session.getLoggedInAt(), session.getAbsoluteExpiresAt()))
                .isEqualTo(lifetime);
        assertThat(session.getIdleTimeoutSeconds()).isEqualTo(idleTimeoutSeconds);
        assertThat(result.response().expiresInSeconds()).isEqualTo(900);
    }

    private RefreshOutcome refreshAfterGate(
            SessionAuthenticationResult login,
            CountDownLatch startGate
    ) throws InterruptedException {
        startGate.await();
        try {
            authSessionService.refresh(
                    login.refreshToken(),
                    login.csrfToken(),
                    login.csrfToken()
            );
            return new RefreshOutcome(ErrorCode.SUCCESS);
        } catch (SessionException exception) {
            return new RefreshOutcome(exception.getErrorCode());
        }
    }

    private UUID sessionIdFor(String rawRefreshToken) {
        return refreshTokenRepository.findReferenceByTokenHash(
                        secureTokenService.hash(rawRefreshToken)
                )
                .orElseThrow()
                .getSessionId();
    }

    private long refreshTokenCount(UUID sessionId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where session_id = ?",
                Long.class,
                sessionId
        );
    }

    private long currentRefreshTokenCount(UUID sessionId) {
        return jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from refresh_tokens
                        where session_id = ?
                          and consumed_at is null
                          and revoked_at is null
                        """,
                Long.class,
                sessionId
        );
    }

    private User createUser(Role role) {
        User user = new User();
        user.setEmail("phase264-" + role.name().toLowerCase() + "-" + UUID.randomUUID()
                + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("StrongPassword123!"));
        user.setFullName("Phase 26.4 " + role.name());
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.saveAndFlush(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private static String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
    }

    private record RefreshOutcome(ErrorCode code) {
    }
}
