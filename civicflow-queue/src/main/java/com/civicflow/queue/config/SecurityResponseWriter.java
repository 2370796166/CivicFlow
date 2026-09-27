package com.civicflow.queue.config;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.GlobalErrorCode;
import com.civicflow.queue.error.QueueSecurityErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SecurityResponseWriter implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    public SecurityResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
                QueueSecurityErrorCode.UNAUTHORIZED);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.access.AccessDeniedException exception)
            throws IOException {
        write(
                request,
                response,
                HttpServletResponse.SC_FORBIDDEN,
                QueueSecurityErrorCode.FORBIDDEN);
    }

    private void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            GlobalErrorCode errorCode)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.failure(errorCode, null, RequestIdFilter.current(request)));
    }
}
