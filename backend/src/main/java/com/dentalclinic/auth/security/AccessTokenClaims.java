package com.dentalclinic.auth.security;

import com.dentalclinic.user.entity.Role;

import java.util.UUID;

public record AccessTokenClaims(UUID userId, UUID sessionId, Role role) {
}
