package com.civicflow.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.civicflow.queue.client.AppointmentQueueStateClient;
import com.civicflow.queue.client.ResourceStaffClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles("test")
@SpringBootTest
class QueueFeignContractIntegrationTest {
    private static final AtomicReference<CapturedRequest> RESOURCE_REQUEST =
            new AtomicReference<>();
    private static final AtomicReference<CapturedRequest> APPOINTMENT_REQUEST =
            new AtomicReference<>();
    private static final HttpServer RESOURCE_SERVER = server();
    private static final HttpServer APPOINTMENT_SERVER = server();

    static {
        RESOURCE_SERVER.createContext(
                "/internal/v1/resource/staff/7001/scopes",
                exchange ->
                        respond(
                                exchange,
                                RESOURCE_REQUEST,
                                """
                                {"code":"OK","message":"Success","data":[{"outletId":"101","windowId":"201","items":[{"id":"301"}]}],"requestId":"resource-test"}
                                """));
        APPOINTMENT_SERVER.createContext(
                "/internal/v1/appointments/100/queue-state",
                exchange ->
                        respond(
                                exchange,
                                APPOINTMENT_REQUEST,
                                """
                                {"code":"OK","message":"Success","data":null,"requestId":"appointment-test"}
                                """));
        RESOURCE_SERVER.start();
        APPOINTMENT_SERVER.start();
    }

    @Autowired private ResourceStaffClient resource;
    @Autowired private AppointmentQueueStateClient appointment;
    @Autowired private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add(
                "civicflow.queue.resource-url",
                () -> "http://127.0.0.1:" + RESOURCE_SERVER.getAddress().getPort());
        registry.add(
                "civicflow.queue.appointment-url",
                () -> "http://127.0.0.1:" + APPOINTMENT_SERVER.getAddress().getPort());
        registry.add("civicflow.queue.resource-service-token", () -> "resource-test-jwt");
        registry.add("civicflow.queue.appointment-service-token", () -> "appointment-test-jwt");
    }

    @AfterAll
    static void closeServers() {
        RESOURCE_SERVER.stop(0);
        APPOINTMENT_SERVER.stop(0);
    }

    @Test
    void sendsVersionedInternalPathsWithDistinctServiceTokens() throws Exception {
        var scopes = resource.scopes(7001L);
        assertThat(scopes.code()).isEqualTo("OK");
        assertThat(scopes.data()).hasSize(1);
        assertThat(scopes.data().get(0).windowId()).isEqualTo("201");
        assertThat(scopes.data().get(0).items().get(0).id()).isEqualTo("301");

        var change =
                appointment.change(
                        100L, new AppointmentQueueStateClient.Change("200", "SERVING", "300"));
        assertThat(change.code()).isEqualTo("OK");

        CapturedRequest resourceRequest = RESOURCE_REQUEST.get();
        assertThat(resourceRequest.method()).isEqualTo("GET");
        assertThat(resourceRequest.path()).isEqualTo("/internal/v1/resource/staff/7001/scopes");
        assertThat(resourceRequest.authorization()).isEqualTo("Bearer resource-test-jwt");
        assertThat(resourceRequest.requestId()).isNotBlank();

        CapturedRequest appointmentRequest = APPOINTMENT_REQUEST.get();
        assertThat(appointmentRequest.method()).isEqualTo("POST");
        assertThat(appointmentRequest.path())
                .isEqualTo("/internal/v1/appointments/100/queue-state");
        assertThat(appointmentRequest.authorization()).isEqualTo("Bearer appointment-test-jwt");
        assertThat(appointmentRequest.requestId()).isNotBlank();
        var body = objectMapper.readTree(appointmentRequest.body());
        assertThat(body.get("ticketId").asText()).isEqualTo("200");
        assertThat(body.get("status").asText()).isEqualTo("SERVING");
        assertThat(body.get("syncId").asText()).isEqualTo("300");
    }

    private static HttpServer server() {
        try {
            return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot open local Feign test server", ex);
        }
    }

    private static void respond(
            HttpExchange exchange, AtomicReference<CapturedRequest> capture, String response)
            throws IOException {
        capture.set(
                new CapturedRequest(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        exchange.getRequestHeaders().getFirst("X-Request-Id"),
                        new String(
                                exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private record CapturedRequest(
            String method, String path, String authorization, String requestId, String body) {}
}
