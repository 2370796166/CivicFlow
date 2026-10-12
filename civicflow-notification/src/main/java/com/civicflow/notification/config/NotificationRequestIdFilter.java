package com.civicflow.notification.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class NotificationRequestIdFilter extends OncePerRequestFilter {
    private static final String ATTRIBUTE = NotificationRequestIdFilter.class.getName();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader("X-Request-Id", id);
        MDC.put("requestId", id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }
    }

    public static String current(HttpServletRequest request) {
        Object id = request.getAttribute(ATTRIBUTE);
        return id == null ? UUID.randomUUID().toString() : id.toString();
    }

    public static String current() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            return current(attributes.getRequest());
        }
        return UUID.randomUUID().toString();
    }
}
