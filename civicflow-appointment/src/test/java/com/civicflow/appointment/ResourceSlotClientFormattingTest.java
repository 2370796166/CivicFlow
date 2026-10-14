package com.civicflow.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.civicflow.appointment.client.ResourceSlotClient;
import com.civicflow.common.api.ApiResponse;
import com.sun.net.httpserver.HttpServer;
import feign.Feign;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.support.SpringMvcContract;

class ResourceSlotClientFormattingTest {
    @Test
    void sendsIsoDateOverHttpToTheExistingUserCandidateEndpoint() throws Exception {
        var query = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/internal/v1/resource/slots/user-candidates",
                exchange -> {
                    query.set(exchange.getRequestURI().getRawQuery());
                    exchange.sendResponseHeaders(200, 0);
                    exchange.getResponseBody().close();
                });
        server.start();
        try {
            var client =
                    Feign.builder()
                            .contract(new SpringMvcContract())
                            .decoder(
                                    (response, type) -> ApiResponse.success(List.of(), "wire-test"))
                            .target(
                                    ResourceSlotClient.class,
                                    "http://127.0.0.1:" + server.getAddress().getPort());
            client.getUserCandidates(1, 2, LocalDate.of(2026, 10, 14));
            assertEquals("outletId=1&itemId=2&date=2026-10-14", query.get());
        } finally {
            server.stop(0);
        }
    }
}
