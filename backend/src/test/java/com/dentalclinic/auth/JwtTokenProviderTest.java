package com.dentalclinic.auth;

import com.dentalclinic.auth.security.JwtProperties;
import com.dentalclinic.auth.security.JwtTokenProvider;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-10-08T10:00:00Z");

    @Test
    void shouldGenerateAndValidateSignedAccessToken() {
        JwtProperties properties = properties(generateSecret());
        JwtTokenProvider provider = provider(properties, ISSUED_AT);
        UserPrincipal principal = principal();

        String token = provider.generateAccessToken(principal);

        assertThat(provider.extractUserId(token)).isEqualTo(principal.getId());
        assertThat(provider.getAccessTokenExpirationSeconds()).isEqualTo(3600L);
    }

    @Test
    void shouldRejectExpiredTokenUsingInjectedClock() {
        JwtProperties properties = properties(generateSecret());
        JwtTokenProvider issuer = provider(properties, ISSUED_AT);
        String token = issuer.generateAccessToken(principal());
        JwtTokenProvider verifier = provider(properties, ISSUED_AT.plusSeconds(3601));

        assertThatThrownBy(() -> verifier.extractUserId(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void shouldRejectTokenSignedWithAnotherKey() {
        JwtTokenProvider issuer = provider(properties(generateSecret()), ISSUED_AT);
        String token = issuer.generateAccessToken(principal());
        JwtTokenProvider verifier = provider(properties(generateSecret()), ISSUED_AT);

        assertThatThrownBy(() -> verifier.extractUserId(token))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void shouldRejectSecretShorterThan256Bits() {
        String shortSecret = Encoders.BASE64.encode(new byte[16]);

        assertThatThrownBy(() -> provider(properties(shortSecret), ISSUED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    private JwtTokenProvider provider(JwtProperties properties, Instant instant) {
        return new JwtTokenProvider(properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private JwtProperties properties(String secret) {
        return new JwtProperties(secret, "dental-clinic-test", 60);
    }

    private String generateSecret() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return Encoders.BASE64.encode(key.getEncoded());
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
}
