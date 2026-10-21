package com.dentalclinic.auth;

import com.dentalclinic.auth.config.AuthSessionProperties;
import com.dentalclinic.auth.entity.AuthSession;
import com.dentalclinic.auth.entity.AuthSessionStatus;
import com.dentalclinic.auth.entity.RefreshToken;
import com.dentalclinic.auth.exception.SessionException;
import com.dentalclinic.auth.repository.AuthSessionRepository;
import com.dentalclinic.auth.repository.RefreshTokenRepository;
import com.dentalclinic.auth.security.AccessTokenClaims;
import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.auth.service.AuthSessionService;
import com.dentalclinic.auth.service.SecureTokenService;
import com.dentalclinic.auth.service.SessionAuthenticationResult;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");

    @Mock
    private AuthSessionRepository authSessionRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private final SecureTokenService secureTokenService = new SecureTokenService();
    private AuthSessionService authSessionService;

    @BeforeEach
    void setUp() {
        AuthSessionProperties properties = new AuthSessionProperties(
                new AuthSessionProperties.RolePolicy(Duration.ofHours(8), Duration.ofMinutes(15)),
                new AuthSessionProperties.RolePolicy(Duration.ofHours(12), Duration.ofMinutes(20)),
                new AuthSessionProperties.RolePolicy(Duration.ofHours(12), Duration.ofMinutes(20)),
                new AuthSessionProperties.RolePolicy(Duration.ofDays(7), null)
        );
        authSessionService = new AuthSessionService(
                authSessionRepository,
                refreshTokenRepository,
                userDetailsService,
                passwordEncoder,
                jwtTokenProvider,
                secureTokenService,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        lenient().when(authSessionRepository.saveAndFlush(any(AuthSession.class)))
                .thenAnswer(invocation -> {
                    AuthSession session = invocation.getArgument(0);
                    if (session.getId() == null) {
                        session.setId(UUID.randomUUID());
                    }
                    return session;
                });
        lenient().when(refreshTokenRepository.saveAndFlush(any(RefreshToken.class)))
                .thenAnswer(invocation -> {
                    RefreshToken token = invocation.getArgument(0);
                    if (token.getId() == null) {
                        token.setId(UUID.randomUUID());
                    }
                    return token;
                });
        lenient().when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class), any(UUID.class)))
                .thenReturn("access-token");
        lenient().when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);
    }

    @Test
    void shouldSnapshotAbsoluteAndIdlePoliciesForAllRoles() {
        for (Role role : Role.values()) {
            authSessionService.startSession(principal(role, UserStatus.ACTIVE));
        }

        ArgumentCaptor<AuthSession> captor = ArgumentCaptor.forClass(AuthSession.class);
        verify(authSessionRepository, org.mockito.Mockito.times(4)).saveAndFlush(captor.capture());
        assertPolicy(captor.getAllValues(), Role.ADMIN, Duration.ofHours(8), 15 * 60);
        assertPolicy(captor.getAllValues(), Role.DOCTOR, Duration.ofHours(12), 20 * 60);
        assertPolicy(captor.getAllValues(), Role.STAFF, Duration.ofHours(12), 20 * 60);
        assertPolicy(captor.getAllValues(), Role.PATIENT, Duration.ofDays(7), null);
    }

    @Test
    void shouldRotateRefreshTokenWithoutExtendingAbsoluteExpiryOrChangingCsrfToken() {
        UserPrincipal principal = principal(Role.DOCTOR, UserStatus.ACTIVE);
        AuthSession session = activeSession(principal, 20 * 60, NOW.plus(Duration.ofHours(12)));
        RefreshToken currentToken = currentToken(session, "current-refresh");
        stubRefreshLookup(session, currentToken, "current-refresh");
        when(userDetailsService.loadUserById(principal.getId())).thenReturn(principal);

        SessionAuthenticationResult result = authSessionService.refresh(
                "current-refresh",
                "csrf-token",
                "csrf-token"
        );

        assertThat(currentToken.getConsumedAt()).isEqualTo(NOW);
        assertThat(currentToken.getReplacedByTokenId()).isNotNull();
        assertThat(result.refreshToken()).isNotBlank().isNotEqualTo("current-refresh");
        assertThat(result.csrfToken()).isEqualTo("csrf-token");
        assertThat(result.cookieMaxAgeSeconds()).isEqualTo(Duration.ofHours(12).toSeconds());
        assertThat(session.getAbsoluteExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
    }

    @Test
    void shouldRevokeWholeSessionWhenConsumedRefreshTokenIsReused() {
        UserPrincipal principal = principal(Role.ADMIN, UserStatus.ACTIVE);
        AuthSession session = activeSession(principal, 15 * 60, NOW.plus(Duration.ofHours(8)));
        RefreshToken reusedToken = currentToken(session, "reused-refresh");
        reusedToken.setConsumedAt(NOW.minusSeconds(10));
        stubRefreshLookup(session, reusedToken, "reused-refresh");
        when(refreshTokenRepository.findUnrevokedForUpdateBySessionId(session.getId()))
                .thenReturn(List.of(reusedToken));

        assertThatThrownBy(() -> authSessionService.refresh(
                "reused-refresh",
                "csrf-token",
                "csrf-token"
        )).isInstanceOfSatisfying(SessionException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_REUSED));

        assertThat(session.getStatus()).isEqualTo(AuthSessionStatus.REVOKED);
        assertThat(session.getRevocationReason()).isEqualTo("REFRESH_TOKEN_REUSE");
        assertThat(reusedToken.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldLockIdleStaffSessionAndAllowPasswordUnlockWithinAbsoluteLifetime() {
        UserPrincipal principal = principal(Role.STAFF, UserStatus.ACTIVE);
        AuthSession session = activeSession(principal, 20 * 60, NOW.plus(Duration.ofHours(12)));
        session.setLastActivityAt(NOW.minus(Duration.ofMinutes(20)));
        when(authSessionRepository.findForUpdateById(session.getId())).thenReturn(Optional.of(session));
        when(userDetailsService.loadUserById(principal.getId())).thenReturn(principal);

        assertThatThrownBy(() -> authSessionService.authenticateAccessToken(
                new AccessTokenClaims(principal.getId(), session.getId(), Role.STAFF)
        )).isInstanceOfSatisfying(SessionException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SESSION_LOCKED));
        assertThat(session.getStatus()).isEqualTo(AuthSessionStatus.LOCKED);
        assertThat(session.getLockedAt()).isEqualTo(NOW);

        RefreshToken refreshToken = currentToken(session, "staff-refresh");
        stubRefreshLookup(session, refreshToken, "staff-refresh");
        when(passwordEncoder.matches("correct-password", principal.getPassword())).thenReturn(true);

        SessionAuthenticationResult result = authSessionService.unlock(
                "staff-refresh",
                "csrf-token",
                "csrf-token",
                "correct-password"
        );

        assertThat(session.getStatus()).isEqualTo(AuthSessionStatus.ACTIVE);
        assertThat(session.getLastActivityAt()).isEqualTo(NOW);
        assertThat(session.getLockedAt()).isNull();
        assertThat(session.getAbsoluteExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
        assertThat(result.response().accessToken()).isEqualTo("access-token");
    }

    @Test
    void shouldRevokeSessionAtAbsoluteExpiry() {
        UserPrincipal principal = principal(Role.PATIENT, UserStatus.ACTIVE);
        AuthSession session = activeSession(principal, null, NOW);
        when(authSessionRepository.findForUpdateById(session.getId())).thenReturn(Optional.of(session));
        when(userDetailsService.loadUserById(principal.getId())).thenReturn(principal);
        when(refreshTokenRepository.findUnrevokedForUpdateBySessionId(session.getId()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> authSessionService.authenticateAccessToken(
                new AccessTokenClaims(principal.getId(), session.getId(), Role.PATIENT)
        )).isInstanceOfSatisfying(SessionException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SESSION_EXPIRED));

        assertThat(session.getStatus()).isEqualTo(AuthSessionStatus.REVOKED);
        assertThat(session.getRevocationReason()).isEqualTo("ABSOLUTE_EXPIRED");
    }

    @Test
    void shouldOnlyAdvanceIdleActivityOnExplicitActivityCall() {
        UserPrincipal principal = principal(Role.DOCTOR, UserStatus.ACTIVE);
        AuthSession session = activeSession(principal, 20 * 60, NOW.plus(Duration.ofHours(12)));
        Instant priorActivity = NOW.minus(Duration.ofMinutes(5));
        session.setLastActivityAt(priorActivity);
        when(authSessionRepository.findForUpdateById(session.getId())).thenReturn(Optional.of(session));
        when(userDetailsService.loadUserById(principal.getId())).thenReturn(principal);

        UserPrincipal authenticated = authSessionService.authenticateAccessToken(
                new AccessTokenClaims(principal.getId(), session.getId(), Role.DOCTOR)
        );
        assertThat(session.getLastActivityAt()).isEqualTo(priorActivity);

        authSessionService.recordActivity(authenticated);
        assertThat(session.getLastActivityAt()).isEqualTo(NOW);
    }

    @Test
    void shouldNotApplyIdleLockToPatientSession() {
        UserPrincipal principal = principal(Role.PATIENT, UserStatus.ACTIVE);
        AuthSession session = activeSession(principal, null, NOW.plus(Duration.ofDays(7)));
        session.setLastActivityAt(NOW.minus(Duration.ofDays(6)));
        when(authSessionRepository.findForUpdateById(session.getId())).thenReturn(Optional.of(session));
        when(userDetailsService.loadUserById(principal.getId())).thenReturn(principal);

        UserPrincipal authenticated = authSessionService.authenticateAccessToken(
                new AccessTokenClaims(principal.getId(), session.getId(), Role.PATIENT)
        );

        assertThat(authenticated.getSessionId()).isEqualTo(session.getId());
        assertThat(session.getStatus()).isEqualTo(AuthSessionStatus.ACTIVE);
        verify(authSessionRepository, never()).save(session);
    }

    private void assertPolicy(
            List<AuthSession> sessions,
            Role role,
            Duration lifetime,
            Integer idleTimeoutSeconds
    ) {
        AuthSession session = sessions.stream()
                .filter(candidate -> candidate.getRole() == role)
                .findFirst()
                .orElseThrow();
        assertThat(session.getLoggedInAt()).isEqualTo(NOW);
        assertThat(session.getAbsoluteExpiresAt()).isEqualTo(NOW.plus(lifetime));
        assertThat(session.getIdleTimeoutSeconds()).isEqualTo(idleTimeoutSeconds);
    }

    private AuthSession activeSession(
            UserPrincipal principal,
            Integer idleTimeoutSeconds,
            Instant absoluteExpiresAt
    ) {
        AuthSession session = new AuthSession();
        session.setId(UUID.randomUUID());
        session.setUserId(principal.getId());
        session.setRole(principal.getRole());
        session.setStatus(AuthSessionStatus.ACTIVE);
        session.setLoggedInAt(NOW.minus(Duration.ofHours(1)));
        session.setAbsoluteExpiresAt(absoluteExpiresAt);
        session.setLastActivityAt(NOW);
        session.setIdleTimeoutSeconds(idleTimeoutSeconds);
        session.setCsrfTokenHash(secureTokenService.hash("csrf-token"));
        return session;
    }

    private RefreshToken currentToken(AuthSession session, String rawToken) {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setSession(session);
        token.setTokenHash(secureTokenService.hash(rawToken));
        token.setIssuedAt(NOW.minusSeconds(60));
        token.setExpiresAt(session.getAbsoluteExpiresAt());
        return token;
    }

    private void stubRefreshLookup(AuthSession session, RefreshToken token, String rawToken) {
        RefreshTokenRepository.TokenReference reference = mock(
                RefreshTokenRepository.TokenReference.class
        );
        when(reference.getSessionId()).thenReturn(session.getId());
        when(reference.getTokenId()).thenReturn(token.getId());
        when(refreshTokenRepository.findReferenceByTokenHash(secureTokenService.hash(rawToken)))
                .thenReturn(Optional.of(reference));
        when(authSessionRepository.findForUpdateById(session.getId())).thenReturn(Optional.of(session));
        when(refreshTokenRepository.findForUpdateById(token.getId())).thenReturn(Optional.of(token));
    }

    private UserPrincipal principal(Role role, UserStatus status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(role.name().toLowerCase() + "@example.com");
        user.setPasswordHash("encoded-password");
        user.setFullName(role.name());
        user.setRole(role);
        user.setStatus(status);
        return UserPrincipal.from(user);
    }
}
