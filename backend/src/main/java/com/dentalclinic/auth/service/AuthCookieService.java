package com.dentalclinic.auth.service;

import com.dentalclinic.auth.config.AuthCookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

@Component
public class AuthCookieService {

    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    public static final String CSRF_TOKEN_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";
    private static final String CSRF_COOKIE_PATH = "/";

    private final AuthCookieProperties properties;

    public AuthCookieService(AuthCookieProperties properties) {
        this.properties = properties;
    }

    public void writeSessionCookies(
            HttpServletResponse response,
            SessionAuthenticationResult authentication
    ) {
        addCookie(response, REFRESH_TOKEN_COOKIE, authentication.refreshToken(), true,
                REFRESH_COOKIE_PATH,
                authentication.cookieMaxAgeSeconds());
        addCookie(response, CSRF_TOKEN_COOKIE, authentication.csrfToken(), false,
                CSRF_COOKIE_PATH,
                authentication.cookieMaxAgeSeconds());
    }

    public void clearSessionCookies(HttpServletResponse response) {
        addCookie(response, REFRESH_TOKEN_COOKIE, "", true, REFRESH_COOKIE_PATH, 0);
        addCookie(response, CSRF_TOKEN_COOKIE, "", false, CSRF_COOKIE_PATH, 0);
    }

    public String readRefreshToken(HttpServletRequest request) {
        return readCookie(request, REFRESH_TOKEN_COOKIE);
    }

    public String readCsrfCookie(HttpServletRequest request) {
        return readCookie(request, CSRF_TOKEN_COOKIE);
    }

    public String readCsrfHeader(HttpServletRequest request) {
        return request.getHeader(CSRF_HEADER);
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void addCookie(
            HttpServletResponse response,
            String name,
            String value,
            boolean httpOnly,
            String path,
            long maxAgeSeconds
    ) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(path)
                .maxAge(Duration.ofSeconds(maxAgeSeconds));
        if (properties.domain() != null) {
            builder.domain(properties.domain());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }
}
