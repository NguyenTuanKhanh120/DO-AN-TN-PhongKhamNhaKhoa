package com.dentalclinic.auth.dto;

import com.dentalclinic.user.entity.Role;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        String email,
        String fullName,
        Role role
) {
    public AuthResponse(
            String accessToken,
            long expiresInSeconds,
            UUID userId,
            String email,
            String fullName,
            Role role
    ) {
        this(accessToken, "Bearer", expiresInSeconds, userId, email, fullName, role);
    }
}
