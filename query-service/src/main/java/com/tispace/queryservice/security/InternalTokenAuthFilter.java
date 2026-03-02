package com.tispace.queryservice.security;

import com.tispace.common.util.LogRateLimiter;
import com.tispace.queryservice.config.InternalSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;

/**
 * Filter to authenticate internal API requests using API Key header.
 * Only applies to paths starting with /internal/
 * Returns 401 if header is missing, 403 if token is invalid.
 * Uses constant-time comparison to prevent timing attacks.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InternalTokenAuthFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final String ACTUATOR_PATH_PREFIX = "/actuator/";
    private static final String ACTUATOR_HEALTH_PATH = "/actuator/health";
    private static final String ACTUATOR_INFO_PATH = "/actuator/info";
    private static final LogRateLimiter LOG_LIMITER = LogRateLimiter.getInstance();
    private static final Duration AUTH_LOG_WINDOW = Duration.ofSeconds(60);

    private final InternalSecurityProperties properties;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestPath = request.getRequestURI();


        if (!isProtectedPath(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }


        String providedToken = request.getHeader(properties.getHeader());

        if (!StringUtils.hasText(providedToken)) {
            if (LOG_LIMITER.shouldLog("auth:missing_header", AUTH_LOG_WINDOW)) {
                log.warn("Missing auth header for internal endpoint: {}", requestPath);
            }
            log.debug("Missing {} header for internal endpoint: {}", properties.getHeader(), requestPath);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized: Missing authentication header\"}");
            return;
        }

        if (!isTokenValid(providedToken)) {
            if (LOG_LIMITER.shouldLog("auth:invalid_token", AUTH_LOG_WINDOW)) {
                log.warn("Invalid token for internal endpoint: {}", requestPath);
            }
            log.debug("Invalid token for internal endpoint: {}", requestPath);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Forbidden: Invalid authentication token\"}");
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                "internal-service",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    private boolean isTokenValid(String providedToken) {
        byte[] expected = properties.getToken().getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }

    private boolean isProtectedPath(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return false;
        }
        if (requestPath.startsWith(INTERNAL_PATH_PREFIX)) {
            return true;
        }
        if (!requestPath.startsWith(ACTUATOR_PATH_PREFIX)) {
            return false;
        }
        return !requestPath.equals(ACTUATOR_HEALTH_PATH)
                && !requestPath.startsWith(ACTUATOR_HEALTH_PATH + "/")
                && !requestPath.equals(ACTUATOR_INFO_PATH);
    }
}

