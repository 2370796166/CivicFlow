package com.civicflow.queue;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.queue.client.AuthCurrentUserClient;
import com.civicflow.queue.config.AdminAuthorizationFilter;
import com.civicflow.queue.config.SecurityResponseWriter;
import com.civicflow.queue.service.AdminAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AdminAuthorizationTest {
    private final AuthCurrentUserClient auth = mock(AuthCurrentUserClient.class);
    private final AdminAuthorizationFilter filter =
            new AdminAuthorizationFilter(
                    new AdminAuthorizationService(auth),
                    new SecurityResponseWriter(new ObjectMapper()));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeAdminPassesAndForwardsOnlyAuthenticatedBearer() throws Exception {
        authenticate();
        when(auth.currentUser(anyString(), anyString(), anyString()))
                .thenReturn(
                        ApiResponse.success(
                                new AuthCurrentUserClient.User("42", List.of("ADMIN")),
                                "auth-request"));
        var reached = new AtomicBoolean();
        var response = new MockHttpServletResponse();
        filter.doFilter(
                new MockHttpServletRequest("POST", "/api/v1/admin/slots"),
                response,
                (a, b) -> reached.set(true));
        assertThat(reached).isTrue();
        verify(auth).currentUser(eq("Bearer verified-token"), anyString(), startsWith("00-"));
    }

    @Test
    void removedAdminAndWrongIdentityBlockAllManagementWriteMethods() throws Exception {
        authenticate();
        for (var user :
                List.of(
                        new AuthCurrentUserClient.User("42", List.of("USER")),
                        new AuthCurrentUserClient.User("43", List.of("ADMIN")))) {
            when(auth.currentUser(anyString(), anyString(), anyString()))
                    .thenReturn(ApiResponse.success(user, "auth-request"));
            for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
                var reached = new AtomicBoolean();
                var response = new MockHttpServletResponse();
                filter.doFilter(
                        new MockHttpServletRequest(method, "/api/v1/admin/slots"),
                        response,
                        (a, b) -> reached.set(true));
                assertThat(reached).isFalse();
                assertThat(response.getStatus()).isEqualTo(403);
                assertThat(response.getContentAsString())
                        .contains("AUTH_403_FORBIDDEN")
                        .doesNotContain("verified-token");
            }
        }
    }

    @Test
    void authOutageFailsClosedAndReadsDoNotDependOnAuth() throws Exception {
        authenticate();
        when(auth.currentUser(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("offline"));
        var reached = new AtomicBoolean();
        var response = new MockHttpServletResponse();
        filter.doFilter(
                new MockHttpServletRequest("POST", "/api/v1/admin/slots"),
                response,
                (a, b) -> reached.set(true));
        assertThat(reached).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("COMMON_503_DEPENDENCY_UNAVAILABLE");
        reset(auth);
        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/admin/slots"),
                new MockHttpServletResponse(),
                (a, b) -> reached.set(true));
        assertThat(reached).isTrue();
        verifyNoInteractions(auth);
    }

    private static void authenticate() {
        var jwt =
                Jwt.withTokenValue("verified-token")
                        .header("alg", "RS256")
                        .subject("42")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new JwtAuthenticationToken(
                                jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
