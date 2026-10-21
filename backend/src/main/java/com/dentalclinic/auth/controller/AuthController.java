package com.dentalclinic.auth.controller;

import com.dentalclinic.auth.dto.AuthResponse;
import com.dentalclinic.auth.dto.AuthSessionResponse;
import com.dentalclinic.auth.dto.LoginRequest;
import com.dentalclinic.auth.dto.RegisterRequest;
import com.dentalclinic.auth.dto.UnlockRequest;
import com.dentalclinic.auth.security.CurrentUser;
import com.dentalclinic.auth.security.UserPrincipal;
import com.dentalclinic.auth.service.AuthCookieService;
import com.dentalclinic.auth.service.AuthSessionService;
import com.dentalclinic.auth.service.AuthService;
import com.dentalclinic.auth.service.SessionAuthenticationResult;
import com.dentalclinic.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthSessionService authSessionService;
    private final AuthCookieService authCookieService;

    public AuthController(
            AuthService authService,
            AuthSessionService authSessionService,
            AuthCookieService authCookieService
    ) {
        this.authService = authService;
        this.authSessionService = authSessionService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse servletResponse
    ) {
        SessionAuthenticationResult authentication = authService.registerPatient(request);
        authCookieService.writeSessionCookies(servletResponse, authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Đăng ký tài khoản thành công",
                        authentication.response()
                ));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse servletResponse
    ) {
        SessionAuthenticationResult authentication = authService.login(request);
        authCookieService.writeSessionCookies(servletResponse, authentication);
        return ApiResponse.success("Đăng nhập thành công", authentication.response());
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        SessionAuthenticationResult authentication = authSessionService.refresh(
                authCookieService.readRefreshToken(request),
                authCookieService.readCsrfCookie(request),
                authCookieService.readCsrfHeader(request)
        );
        authCookieService.writeSessionCookies(response, authentication);
        return ApiResponse.success("Làm mới Access Token thành công", authentication.response());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authSessionService.logout(
                authCookieService.readRefreshToken(request),
                authCookieService.readCsrfCookie(request),
                authCookieService.readCsrfHeader(request)
        );
        authCookieService.clearSessionCookies(response);
        return ApiResponse.success("Đăng xuất thành công", null);
    }

    @PostMapping("/activity")
    public ApiResponse<AuthSessionResponse> activity(@CurrentUser UserPrincipal currentUser) {
        return ApiResponse.success(
                "Cập nhật hoạt động phiên thành công",
                authSessionService.recordActivity(currentUser)
        );
    }

    @PostMapping("/unlock")
    public ApiResponse<AuthResponse> unlock(
            @Valid @RequestBody UnlockRequest unlockRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        SessionAuthenticationResult authentication = authSessionService.unlock(
                authCookieService.readRefreshToken(request),
                authCookieService.readCsrfCookie(request),
                authCookieService.readCsrfHeader(request),
                unlockRequest.password()
        );
        authCookieService.writeSessionCookies(response, authentication);
        return ApiResponse.success("Mở khóa phiên thành công", authentication.response());
    }

    @GetMapping("/session")
    public ApiResponse<AuthSessionResponse> session(@CurrentUser UserPrincipal currentUser) {
        return ApiResponse.success(authSessionService.getSession(currentUser));
    }
}
