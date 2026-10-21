package com.dentalclinic.auth.service;

import com.dentalclinic.auth.config.AuthSessionProperties;
import com.dentalclinic.auth.dto.AuthResponse;
import com.dentalclinic.auth.dto.AuthSessionResponse;
import com.dentalclinic.auth.entity.AuthSession;
import com.dentalclinic.auth.entity.AuthSessionStatus;
import com.dentalclinic.auth.entity.RefreshToken;
import com.dentalclinic.auth.exception.SessionException;
import com.dentalclinic.auth.repository.AuthSessionRepository;
import com.dentalclinic.auth.repository.RefreshTokenRepository;
import com.dentalclinic.auth.security.AccessTokenClaims;
import com.dentalclinic.auth.security.BcryptPasswordValidator;
import com.dentalclinic.auth.security.CustomUserDetailsService;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.UnauthorizedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthSessionService {

    private static final String REASON_ABSOLUTE_EXPIRED = "ABSOLUTE_EXPIRED";
    private static final String REASON_ACCOUNT_INACTIVE = "ACCOUNT_INACTIVE";
    private static final String REASON_LOGOUT = "LOGOUT";
    private static final String REASON_TOKEN_REUSE = "REFRESH_TOKEN_REUSE";
    private static final String REASON_ADMIN_DEACTIVATION = "ACCOUNT_DEACTIVATED";

    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecureTokenService secureTokenService;
    private final AuthSessionProperties sessionProperties;
    private final Clock clock;

    public AuthSessionService(
            AuthSessionRepository authSessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            SecureTokenService secureTokenService,
            AuthSessionProperties sessionProperties,
            Clock clock
    ) {
        this.authSessionRepository = authSessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.secureTokenService = secureTokenService;
        this.sessionProperties = sessionProperties;
        this.clock = clock;
    }

    @Transactional
    public SessionAuthenticationResult startSession(UserPrincipal principal) {
        Instant now = clock.instant();
        AuthSessionProperties.RolePolicy policy = sessionProperties.policyFor(principal.getRole());
        String csrfToken = secureTokenService.generateToken();

        AuthSession session = new AuthSession();
        session.setUserId(principal.getId());
        session.setRole(principal.getRole());
        session.setStatus(AuthSessionStatus.ACTIVE);
        session.setLoggedInAt(now);
        session.setAbsoluteExpiresAt(now.plus(policy.absoluteLifetime()));
        session.setLastActivityAt(now);
        session.setIdleTimeoutSeconds(toIdleTimeoutSeconds(policy.idleTimeout()));
        session.setCsrfTokenHash(secureTokenService.hash(csrfToken));
        authSessionRepository.saveAndFlush(session);

        IssuedRefreshToken refreshToken = createRefreshToken(session, now);
        return authenticationResult(principal, session, refreshToken.rawToken(), csrfToken, now);
    }

    @Transactional(noRollbackFor = SessionException.class)
    public UserPrincipal authenticateAccessToken(AccessTokenClaims claims) {
        Instant now = clock.instant();
        AuthSession session = requireSessionForUpdate(claims.sessionId());
        if (!session.getUserId().equals(claims.userId()) || session.getRole() != claims.role()) {
            throw new SessionException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        UserPrincipal principal = loadCurrentPrincipal(session, now);
        requireUsableSession(session, now);
        return principal.withSessionId(session.getId());
    }

    @Transactional(noRollbackFor = SessionException.class)
    public SessionAuthenticationResult refresh(
            String rawRefreshToken,
            String csrfCookie,
            String csrfHeader
    ) {
        Instant now = clock.instant();
        LockedRefreshToken locked = resolveAndLockRefreshToken(rawRefreshToken);
        validateCsrf(locked.session(), csrfCookie, csrfHeader);
        requireCurrentRefreshToken(locked, now);

        UserPrincipal principal = loadCurrentPrincipal(locked.session(), now);
        requireUsableSession(locked.session(), now);
        return rotate(locked, principal, csrfCookie, now);
    }

    @Transactional(noRollbackFor = {SessionException.class, UnauthorizedException.class})
    public SessionAuthenticationResult unlock(
            String rawRefreshToken,
            String csrfCookie,
            String csrfHeader,
            String password
    ) {
        BcryptPasswordValidator.validate(password);
        Instant now = clock.instant();
        LockedRefreshToken locked = resolveAndLockRefreshToken(rawRefreshToken);
        validateCsrf(locked.session(), csrfCookie, csrfHeader);
        requireCurrentRefreshToken(locked, now);

        AuthSession session = locked.session();
        if (session.getIdleTimeoutSeconds() == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Vai trò này không áp dụng khóa phiên do không hoạt động"
            );
        }
        UserPrincipal principal = loadCurrentPrincipal(session, now);
        requireNotExpiredOrRevoked(session, now);
        lockIfIdle(session, now);
        if (session.getStatus() != AuthSessionStatus.LOCKED) {
            throw new ConflictException("Phiên đăng nhập chưa bị khóa");
        }
        if (!passwordEncoder.matches(password, principal.getPassword())) {
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS);
        }

        session.setStatus(AuthSessionStatus.ACTIVE);
        session.setLockedAt(null);
        session.setLastActivityAt(now);
        return rotate(locked, principal, csrfCookie, now);
    }

    @Transactional(noRollbackFor = SessionException.class)
    public AuthSessionResponse recordActivity(UserPrincipal principal) {
        requireAuthenticatedSession(principal);
        Instant now = clock.instant();
        AuthSession session = requireOwnedSession(principal);
        UserPrincipal currentPrincipal = loadCurrentPrincipal(session, now);
        requireUsableSession(session, now);
        if (session.getIdleTimeoutSeconds() == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Vai trò này không áp dụng cập nhật hoạt động phiên"
            );
        }
        if (currentPrincipal.getRole() != principal.getRole()) {
            throw new SessionException(ErrorCode.SESSION_REVOKED);
        }
        session.setLastActivityAt(now);
        authSessionRepository.saveAndFlush(session);
        return toResponse(session);
    }

    @Transactional(noRollbackFor = SessionException.class)
    public AuthSessionResponse getSession(UserPrincipal principal) {
        requireAuthenticatedSession(principal);
        Instant now = clock.instant();
        AuthSession session = requireOwnedSession(principal);
        loadCurrentPrincipal(session, now);
        requireUsableSession(session, now);
        return toResponse(session);
    }

    @Transactional
    public void logout(String rawRefreshToken, String csrfCookie, String csrfHeader) {
        LockedRefreshToken locked = resolveAndLockRefreshToken(rawRefreshToken);
        validateCsrf(locked.session(), csrfCookie, csrfHeader);
        revokeSession(locked.session(), clock.instant(), REASON_LOGOUT);
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        Instant now = clock.instant();
        authSessionRepository.findActiveForUpdateByUserId(
                        userId,
                        AuthSessionStatus.REVOKED
                )
                .forEach(session -> revokeSession(session, now, REASON_ADMIN_DEACTIVATION));
    }

    private SessionAuthenticationResult rotate(
            LockedRefreshToken locked,
            UserPrincipal principal,
            String csrfToken,
            Instant now
    ) {
        RefreshToken currentToken = locked.token();
        currentToken.setConsumedAt(now);
        refreshTokenRepository.saveAndFlush(currentToken);

        IssuedRefreshToken replacement = createRefreshToken(locked.session(), now);
        currentToken.setReplacedByTokenId(replacement.entity().getId());
        refreshTokenRepository.save(currentToken);

        return authenticationResult(
                principal,
                locked.session(),
                replacement.rawToken(),
                csrfToken,
                now
        );
    }

    private IssuedRefreshToken createRefreshToken(AuthSession session, Instant issuedAt) {
        String rawToken = secureTokenService.generateToken();
        RefreshToken token = new RefreshToken();
        token.setSession(session);
        token.setTokenHash(secureTokenService.hash(rawToken));
        token.setIssuedAt(issuedAt);
        token.setExpiresAt(session.getAbsoluteExpiresAt());
        RefreshToken persistedToken = refreshTokenRepository.saveAndFlush(token);
        return new IssuedRefreshToken(rawToken, persistedToken);
    }

    private SessionAuthenticationResult authenticationResult(
            UserPrincipal principal,
            AuthSession session,
            String refreshToken,
            String csrfToken,
            Instant now
    ) {
        AuthResponse response = new AuthResponse(
                jwtTokenProvider.generateAccessToken(principal, session.getId()),
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                principal.getId(),
                principal.getEmail(),
                principal.getFullName(),
                principal.getRole()
        );
        long maxAge = Math.max(0, Duration.between(now, session.getAbsoluteExpiresAt()).toSeconds());
        return new SessionAuthenticationResult(response, refreshToken, csrfToken, maxAge);
    }

    private LockedRefreshToken resolveAndLockRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new SessionException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        String tokenHash = secureTokenService.hash(rawRefreshToken);
        RefreshTokenRepository.TokenReference reference = refreshTokenRepository
                .findReferenceByTokenHash(tokenHash)
                .orElseThrow(() -> new SessionException(ErrorCode.REFRESH_TOKEN_INVALID));
        AuthSession session = requireSessionForUpdate(reference.getSessionId());
        RefreshToken token = refreshTokenRepository.findForUpdateById(reference.getTokenId())
                .orElseThrow(() -> new SessionException(ErrorCode.REFRESH_TOKEN_INVALID));
        if (!secureTokenService.matches(rawRefreshToken, token.getTokenHash())
                || !token.getSession().getId().equals(session.getId())) {
            throw new SessionException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        return new LockedRefreshToken(session, token);
    }

    private void requireCurrentRefreshToken(LockedRefreshToken locked, Instant now) {
        RefreshToken token = locked.token();
        if (token.getConsumedAt() != null || token.getRevokedAt() != null) {
            revokeSession(locked.session(), now, REASON_TOKEN_REUSE);
            throw new SessionException(ErrorCode.REFRESH_TOKEN_REUSED);
        }
        if (!now.isBefore(token.getExpiresAt())) {
            revokeSession(locked.session(), now, REASON_ABSOLUTE_EXPIRED);
            throw new SessionException(ErrorCode.SESSION_EXPIRED);
        }
    }

    private UserPrincipal loadCurrentPrincipal(AuthSession session, Instant now) {
        final UserPrincipal principal;
        try {
            principal = userDetailsService.loadUserById(session.getUserId());
        } catch (UsernameNotFoundException exception) {
            revokeSession(session, now, REASON_ACCOUNT_INACTIVE);
            throw new SessionException(ErrorCode.SESSION_REVOKED);
        }
        if (!principal.isEnabled()) {
            revokeSession(session, now, REASON_ACCOUNT_INACTIVE);
            throw new SessionException(ErrorCode.ACCOUNT_INACTIVE);
        }
        if (principal.getRole() != session.getRole()) {
            revokeSession(session, now, REASON_ACCOUNT_INACTIVE);
            throw new SessionException(ErrorCode.SESSION_REVOKED);
        }
        return principal;
    }

    private void requireUsableSession(AuthSession session, Instant now) {
        requireNotExpiredOrRevoked(session, now);
        lockIfIdle(session, now);
        if (session.getStatus() == AuthSessionStatus.LOCKED) {
            throw new SessionException(ErrorCode.SESSION_LOCKED);
        }
    }

    private void requireNotExpiredOrRevoked(AuthSession session, Instant now) {
        if (session.getStatus() == AuthSessionStatus.REVOKED) {
            throw new SessionException(ErrorCode.SESSION_REVOKED);
        }
        if (!now.isBefore(session.getAbsoluteExpiresAt())) {
            revokeSession(session, now, REASON_ABSOLUTE_EXPIRED);
            throw new SessionException(ErrorCode.SESSION_EXPIRED);
        }
    }

    private void lockIfIdle(AuthSession session, Instant now) {
        Integer idleTimeoutSeconds = session.getIdleTimeoutSeconds();
        if (session.getStatus() == AuthSessionStatus.ACTIVE
                && idleTimeoutSeconds != null
                && !now.isBefore(session.getLastActivityAt().plusSeconds(idleTimeoutSeconds))) {
            session.setStatus(AuthSessionStatus.LOCKED);
            session.setLockedAt(now);
            authSessionRepository.saveAndFlush(session);
        }
    }

    private void revokeSession(AuthSession session, Instant now, String reason) {
        if (session.getStatus() != AuthSessionStatus.REVOKED) {
            session.setStatus(AuthSessionStatus.REVOKED);
            session.setRevokedAt(now);
            session.setRevocationReason(reason);
            session.setLockedAt(null);
            authSessionRepository.save(session);
        }
        List<RefreshToken> activeTokens = refreshTokenRepository
                .findUnrevokedForUpdateBySessionId(session.getId());
        activeTokens.forEach(token -> token.setRevokedAt(now));
        refreshTokenRepository.saveAll(activeTokens);
        authSessionRepository.flush();
        refreshTokenRepository.flush();
    }

    private void validateCsrf(AuthSession session, String csrfCookie, String csrfHeader) {
        if (!secureTokenService.matches(csrfCookie, session.getCsrfTokenHash())
                || !secureTokenService.matches(csrfHeader, session.getCsrfTokenHash())) {
            throw new SessionException(ErrorCode.CSRF_VALIDATION_FAILED);
        }
    }

    private AuthSession requireOwnedSession(UserPrincipal principal) {
        AuthSession session = requireSessionForUpdate(principal.getSessionId());
        if (!session.getUserId().equals(principal.getId()) || session.getRole() != principal.getRole()) {
            throw new SessionException(ErrorCode.SESSION_REVOKED);
        }
        return session;
    }

    private AuthSession requireSessionForUpdate(UUID sessionId) {
        return authSessionRepository.findForUpdateById(sessionId)
                .orElseThrow(() -> new SessionException(ErrorCode.SESSION_REVOKED));
    }

    private void requireAuthenticatedSession(UserPrincipal principal) {
        if (principal.getSessionId() == null) {
            throw new SessionException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    private Integer toIdleTimeoutSeconds(Duration idleTimeout) {
        if (idleTimeout == null) {
            return null;
        }
        return Math.toIntExact(idleTimeout.toSeconds());
    }

    private AuthSessionResponse toResponse(AuthSession session) {
        return new AuthSessionResponse(
                session.getId(),
                session.getRole(),
                session.getStatus(),
                session.getLoggedInAt(),
                session.getAbsoluteExpiresAt(),
                session.getLastActivityAt(),
                session.getLockedAt(),
                session.getIdleTimeoutSeconds(),
                session.getIdleTimeoutSeconds() != null
        );
    }

    private record LockedRefreshToken(AuthSession session, RefreshToken token) {
    }

    private record IssuedRefreshToken(String rawToken, RefreshToken entity) {
    }
}
