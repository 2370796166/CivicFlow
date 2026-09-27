package com.civicflow.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.civicflow.queue.client.AppointmentCheckInClient;
import com.civicflow.queue.mapper.QueueTicketMapper;
import com.civicflow.queue.service.impl.QueueTicketCreationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql("/checkin-h2.sql")
class CheckInTicketIntegrationTest {
    @Autowired QueueTicketCreationService creation;
    @Autowired QueueTicketMapper tickets;
    @Autowired JdbcTemplate jdbc;

    @Test
    void repeatAndConcurrentClaimCreateOneTicket() throws Exception {
        var claim =
                new AppointmentCheckInClient.ClaimResponse(
                        UUID.randomUUID().toString(),
                        "1001",
                        "2001",
                        "3001",
                        "4001",
                        LocalDate.of(2026, 9, 25),
                        Instant.parse("2026-09-25T00:30:00Z"),
                        "CHECKED_IN");
        var first = creation.create(claim);
        assertEquals(first.getId(), creation.create(claim).getId());
        assertEquals("A001", first.getTicketNo());

        var parallelClaim =
                new AppointmentCheckInClient.ClaimResponse(
                        UUID.randomUUID().toString(),
                        "1002",
                        "2002",
                        "3001",
                        "4001",
                        LocalDate.of(2026, 9, 25),
                        Instant.parse("2026-09-25T00:30:00Z"),
                        "CHECKED_IN");
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a =
                    executor.submit(
                            () -> {
                                start.await();
                                return creation.create(parallelClaim);
                            });
            var b =
                    executor.submit(
                            () -> {
                                start.await();
                                return creation.create(parallelClaim);
                            });
            start.countDown();
            try {
                a.get();
            } catch (java.util.concurrent.ExecutionException duplicate) {
                // The appointment unique key rejects the losing concurrent insert.
            }
            try {
                b.get();
            } catch (java.util.concurrent.ExecutionException duplicate) {
                // The appointment unique key rejects the losing concurrent insert.
            }
        } finally {
            executor.shutdownNow();
        }
        assertNotNull(tickets.selectByAppointment(1002));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM queue_ticket", Integer.class));
        assertEquals(
                2,
                jdbc.queryForObject("SELECT next_number FROM queue_ticket_counter", Integer.class));
    }
}
