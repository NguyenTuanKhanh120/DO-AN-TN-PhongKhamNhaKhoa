package com.dentalclinic.auth.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Locale;
import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "security.auth-cookie")
public record AuthCookieProperties(
        boolean secure,
        @NotBlank String sameSite,
        String domain
) {
    private static final Set<String> ALLOWED_SAME_SITE = Set.of("Lax", "Strict", "None");

    public AuthCookieProperties {
        if (sameSite == null || sameSite.isBlank()) {
            throw new IllegalArgumentException("SameSite must be configured");
        }
        String trimmedSameSite = sameSite.trim();
        String normalized = trimmedSameSite.substring(0, 1).toUpperCase(Locale.ROOT)
                + trimmedSameSite.substring(1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_SAME_SITE.contains(normalized)) {
            throw new IllegalArgumentException("SameSite must be Lax, Strict or None");
        }
        if ("None".equals(normalized) && !secure) {
            throw new IllegalArgumentException("SameSite=None requires Secure cookies");
        }
        sameSite = normalized;
        domain = domain == null || domain.isBlank() ? null : domain.trim();
    }
}
