package com.civicflow.notification.config;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.notification.error.NotificationErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class NotificationSecurityResponseWriter
        implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper mapper;

    public NotificationSecurityResponseWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {
        write(
                request,
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                NotificationErrorCode.UNAUTHORIZED);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception)
            throws IOException {
        write(request, response, HttpServletResponse.SC_FORBIDDEN, NotificationErrorCode.FORBIDDEN);
    }

    private void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            NotificationErrorCode code)
            throws IOException {
        String requestId = NotificationRequestIdFilter.current(request);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Request-Id", requestId);
        mapper.writeValue(response.getOutputStream(), ApiResponse.failure(code, null, requestId));
    }
}
