package com.dentalclinic.auth;

import com.dentalclinic.auth.config.AuthCookieProperties;
import com.dentalclinic.auth.dto.AuthResponse;
import com.dentalclinic.auth.service.AuthCookieService;
import com.dentalclinic.auth.service.SessionAuthenticationResult;
import com.dentalclinic.user.entity.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieServiceTest {

    @Test
    void shouldWriteRefreshAsHttpOnlySecureCookieAndCsrfAsReadableSecureCookie() {
        AuthCookieService service = new AuthCookieService(
                new AuthCookieProperties(true, "Lax", null)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.writeSessionCookies(response, authenticationResult());

        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .contains("refresh_token=refresh-token")
                .contains("Path=/api/auth")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Max-Age=43200"));
        assertThat(cookies).anySatisfy(cookie -> {
            assertThat(cookie)
                    .contains("XSRF-TOKEN=csrf-token")
                    .contains("Path=/")
                    .contains("Secure")
                    .contains("SameSite=Lax");
            assertThat(cookie).doesNotContain("HttpOnly");
        });
    }

    @Test
    void shouldReadRefreshAndDoubleSubmitCsrfValues() {
        AuthCookieService service = new AuthCookieService(
                new AuthCookieProperties(false, "Strict", null)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, "refresh-token"),
                new Cookie(AuthCookieService.CSRF_TOKEN_COOKIE, "csrf-token")
        );
        request.addHeader(AuthCookieService.CSRF_HEADER, "csrf-token");

        assertThat(service.readRefreshToken(request)).isEqualTo("refresh-token");
        assertThat(service.readCsrfCookie(request)).isEqualTo("csrf-token");
        assertThat(service.readCsrfHeader(request)).isEqualTo("csrf-token");
    }

    private SessionAuthenticationResult authenticationResult() {
        return new SessionAuthenticationResult(
                new AuthResponse(
                        "access-token",
                        900,
                        UUID.randomUUID(),
                        "doctor@example.com",
                        "Bác sĩ kiểm thử",
                        Role.DOCTOR
                ),
                "refresh-token",
                "csrf-token",
                43200
        );
    }
}
