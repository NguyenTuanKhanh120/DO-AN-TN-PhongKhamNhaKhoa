package com.dentalclinic.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.dentalclinic.user.entity.Role;

@Component
public class JwtTokenProvider {

    private static final int MINIMUM_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;
    private final Duration accessTokenLifetime;

    public JwtTokenProvider(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = createSigningKey(properties.secret());
        this.accessTokenLifetime = Duration.ofMinutes(properties.accessTokenExpirationMinutes());
    }

    public String generateAccessToken(UserPrincipal principal, UUID sessionId) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(accessTokenLifetime);

        return Jwts.builder()
                .subject(principal.getId().toString())
                .issuer(properties.issuer())
                .claim("role", principal.getRole().name())
                .claim("sid", sessionId.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public UUID extractUserId(String token) {
        return extractAccessTokenClaims(token).userId();
    }

    public AccessTokenClaims extractAccessTokenClaims(String token) {
        Claims claims = parseClaims(token);
        try {
            UUID userId = UUID.fromString(claims.getSubject());
            UUID sessionId = UUID.fromString(claims.get("sid", String.class));
            Role role = Role.valueOf(claims.get("role", String.class));
            return new AccessTokenClaims(userId, sessionId, role);
        } catch (RuntimeException exception) {
            throw new MalformedJwtException("JWT session claims are invalid", exception);
        }
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenLifetime.toSeconds();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey createSigningKey(String encodedSecret) {
        final byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(encodedSecret);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("JWT_SECRET must be valid Base64", exception);
        }

        if (keyBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must contain at least 256 bits after Base64 decoding");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
