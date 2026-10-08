package com.dentalclinic.user.dto;

import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String fullName,
        Role role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
