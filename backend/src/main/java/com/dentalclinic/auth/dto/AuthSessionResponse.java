package com.dentalclinic.auth.dto;

import com.dentalclinic.auth.entity.AuthSessionStatus;
import com.dentalclinic.user.entity.Role;

import java.time.Instant;
import java.util.UUID;

public record AuthSessionResponse(
        UUID sessionId,
        Role role,
        AuthSessionStatus status,
        Instant loggedInAt,
        Instant absoluteExpiresAt,
        Instant lastActivityAt,
        Instant lockedAt,
        Integer idleTimeoutSeconds,
        boolean idleLockEnabled
) {
}
