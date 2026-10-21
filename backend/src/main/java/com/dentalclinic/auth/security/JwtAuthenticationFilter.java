package com.dentalclinic.auth.security;

import com.dentalclinic.auth.exception.SessionException;
import com.dentalclinic.auth.service.AuthSessionService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthSessionService authSessionService;
    private final RestSecurityExceptionHandler securityExceptionHandler;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            AuthSessionService authSessionService,
            RestSecurityExceptionHandler securityExceptionHandler
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authSessionService = authSessionService;
        this.securityExceptionHandler = securityExceptionHandler;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AccessTokenClaims claims = jwtTokenProvider.extractAccessTokenClaims(token);
            UserPrincipal principal = authSessionService.authenticateAccessToken(claims);

            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            principal,
                            token,
                            principal.getAuthorities()
                    );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (SessionException exception) {
            SecurityContextHolder.clearContext();
            securityExceptionHandler.writeError(
                    response,
                    request,
                    exception.getErrorCode()
            );
        } catch (JwtException | AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            securityExceptionHandler.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid access token", exception)
            );
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
