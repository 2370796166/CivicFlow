package com.civicflow.notification.config;

import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.notification.service.AdminAuthorizationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** Installed only inside the security chain, after local JWT authentication. */
public class AdminAuthorizationFilter extends OncePerRequestFilter {
    private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final AdminAuthorizationService service;
    private final NotificationSecurityResponseWriter writer;

    public AdminAuthorizationFilter(
            AdminAuthorizationService service, NotificationSecurityResponseWriter writer) {
        this.service = service;
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/v1/admin/") || !WRITES.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt
                && jwt.isAuthenticated()
                && jwt.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            try {
                service.validate(
                        jwt.getToken().getTokenValue(),
                        jwt.getName(),
                        NotificationRequestIdFilter.current(request));
            } catch (BusinessException exception) {
                writer.write(
                        request,
                        response,
                        exception.errorCode() == CommonErrorCode.DEPENDENCY_UNAVAILABLE ? 503 : 403,
                        exception.errorCode());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
