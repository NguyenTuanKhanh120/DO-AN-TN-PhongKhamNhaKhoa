package com.dentalclinic.auth.service;

import com.dentalclinic.auth.dto.AuthResponse;

public record SessionAuthenticationResult(
        AuthResponse response,
        String refreshToken,
        String csrfToken,
        long cookieMaxAgeSeconds
) {
}
