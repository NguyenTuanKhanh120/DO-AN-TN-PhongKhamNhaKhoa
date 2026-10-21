package com.dentalclinic.auth.config;

import com.dentalclinic.user.entity.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "security.session")
public record AuthSessionProperties(
        @Valid @NotNull RolePolicy admin,
        @Valid @NotNull RolePolicy doctor,
        @Valid @NotNull RolePolicy staff,
        @Valid @NotNull RolePolicy patient
) {

    public RolePolicy policyFor(Role role) {
        return switch (role) {
            case ADMIN -> admin;
            case DOCTOR -> doctor;
            case STAFF -> staff;
            case PATIENT -> patient;
        };
    }

    public record RolePolicy(
            @NotNull Duration absoluteLifetime,
            Duration idleTimeout
    ) {
        public RolePolicy {
            if (absoluteLifetime != null && (absoluteLifetime.isZero() || absoluteLifetime.isNegative())) {
                throw new IllegalArgumentException("Session absolute lifetime must be positive");
            }
            if (idleTimeout != null && (idleTimeout.isZero() || idleTimeout.isNegative())) {
                throw new IllegalArgumentException("Session idle timeout must be positive");
            }
        }
    }
}
