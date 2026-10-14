package com.civicflow.resource;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@DirtiesContext
class LiveAdminAuthorizationIntegrationTest {
    private static final AtomicInteger HTTP_STATUS = new AtomicInteger(200);
    private static final AtomicReference<String> ROLE = new AtomicReference<>("ADMIN");
    private static final AtomicReference<String> BEARER = new AtomicReference<>();
    private static final HttpServer AUTH = startAuth();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void client(DynamicPropertyRegistry registry) {
        registry.add(
                "civicflow.resource.auth-url",
                () -> "http://127.0.0.1:" + AUTH.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        AUTH.stop(0);
    }

    @Test
    void actualFeignAndSecurityChainBlockRevokedAndUnavailableWrites() throws Exception {
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM service_item", Long.class);
        performWrite(200);
        assertThat(BEARER.get()).isEqualTo("Bearer verified-token");
        ROLE.set("USER");
        performWrite(403);
        ROLE.set("ADMIN");
        HTTP_STATUS.set(401);
        performWrite(403);
        HTTP_STATUS.set(503);
        performWrite(503);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM service_item", Long.class))
                .isEqualTo(before + 1);
    }

    private void performWrite(int expected) throws Exception {
        String code = "AUTH_" + UUID.randomUUID().toString().replace("-", "");
        mvc.perform(
                        post("/api/v1/admin/items")
                                .with(
                                        jwt().jwt(
                                                        token ->
                                                                token.subject("42")
                                                                        .tokenValue(
                                                                                "verified-token"))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_ADMIN")))
                                .header("Idempotency-Key", code)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"code\":\""
                                                + code
                                                + "\",\"name\":\"Synthetic item\",\"defaultDurationMinutes\":15}"))
                .andExpect(status().is(expected));
    }

    private static HttpServer startAuth() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/api/v1/user/me",
                    exchange -> {
                        BEARER.set(exchange.getRequestHeaders().getFirst("Authorization"));
                        byte[] body =
                                ("{\"code\":\"OK\",\"message\":\"success\",\"requestId\":\"auth-test\",\"data\":{\"id\":\"42\",\"roles\":[\""
                                                + ROLE.get()
                                                + "\"]}}")
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(HTTP_STATUS.get(), body.length);
                        exchange.getResponseBody().write(body);
                        exchange.close();
                    });
            server.start();
            return server;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
