package com.civicflow.queue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.queue.client.ResourceStaffClient;
import com.civicflow.queue.mapper.QueueStateSyncMapper;
import com.civicflow.queue.service.QueueWorkbenchService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/workbench-h2.sql")
class QueueWorkbenchIntegrationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.civicflow.queue.service.AdminAuthorizationService adminAuthorization;

    @Autowired QueueWorkbenchService workbench;
    @Autowired JdbcTemplate jdbc;
    @Autowired QueueStateSyncMapper stateSync;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @MockBean ResourceStaffClient resources;

    @BeforeEach
    void scopes() {
        when(resources.scopes(anyLong()))
                .thenAnswer(
                        invocation -> {
                            long staff = invocation.getArgument(0);
                            long window = staff == 11 ? 101 : 102;
                            return ApiResponse.success(
                                    List.of(
                                            new ResourceStaffClient.Scope(
                                                    "201",
                                                    Long.toString(window),
                                                    List.of(new ResourceStaffClient.Item("301")))),
                                    "test");
                        });
    }

    private void ticket(long id, int priority) {
        jdbc.update(
                "INSERT INTO queue_ticket(id,appointment_id,user_id,outlet_id,item_id,service_date,ticket_no,priority,status,checked_in_at) VALUES(?,?,?,?,?,?,?,?,'WAITING',CURRENT_TIMESTAMP)",
                id,
                id + 1000,
                id + 2000,
                201,
                301,
                LocalDate.now(ZoneId.of("Asia/Shanghai")),
                "A" + id,
                priority);
    }

    @Test
    void twoWindowsNeverClaimSameTicket() throws Exception {
        ticket(1, 0);
        ticket(2, 1);
        long a = Long.parseLong(workbench.start(11, 101, "start-a", "r1").id());
        long b = Long.parseLong(workbench.start(12, 102, "start-b", "r2").id());
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first =
                    pool.submit(
                            () -> {
                                ready.countDown();
                                go.await();
                                return workbench.callNext(11, a, null, "call-a", "r3");
                            });
            var second =
                    pool.submit(
                            () -> {
                                ready.countDown();
                                go.await();
                                return workbench.callNext(12, b, null, "call-b", "r4");
                            });
            ready.await();
            go.countDown();
            var one = first.get();
            var two = second.get();
            assertTrue(one != null || two != null);
            if (one != null && two != null) assertNotEquals(one.id(), two.id());
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM queue_ticket WHERE id=2 AND status='CALLED'",
                            Integer.class));
            assertEquals(
                    one == null || two == null ? 1 : 2,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM queue_ticket WHERE status='CALLED'",
                            Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void syncLeaseRejectsCompetingAndStaleOwners() {
        stateSync.enqueue(90, 91, 92, "SERVING");
        jdbc.update(
                "UPDATE queue_state_sync SET next_attempt_at=? WHERE id=90",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)));
        assertEquals(1, stateSync.claim(90, "owner-a", java.time.Instant.now().plusSeconds(30)));
        assertEquals(0, stateSync.claim(90, "owner-b", java.time.Instant.now().plusSeconds(30)));
        assertEquals(0, stateSync.done(90, "owner-b"));
        jdbc.update(
                "UPDATE queue_state_sync SET lease_until=? WHERE id=90",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)));
        assertEquals(1, stateSync.claim(90, "owner-b", java.time.Instant.now().plusSeconds(30)));
        assertEquals(0, stateSync.done(90, "owner-a"));
        assertEquals(1, stateSync.done(90, "owner-b"));
    }

    @Test
    void administratorCanInspectEfficiencyAndAuditedRetry() throws Exception {
        ticket(90, 0);
        var base = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        jdbc.update(
                "UPDATE queue_ticket SET status='COMPLETED',called_window_id=101,checked_in_at=?,called_at=?,serving_at=?,completed_at=? WHERE id=90",
                java.sql.Timestamp.from(base.minusSeconds(180)),
                java.sql.Timestamp.from(base.minusSeconds(120)),
                java.sql.Timestamp.from(base.minusSeconds(100)),
                java.sql.Timestamp.from(base.minusSeconds(10)));
        var admin =
                org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(j -> j.subject("1"))
                        .authorities(
                                new org.springframework.security.core.authority
                                        .SimpleGrantedAuthority("ROLE_ADMIN"));
        var user =
                org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(j -> j.subject("7"))
                        .authorities(
                                new org.springframework.security.core.authority
                                        .SimpleGrantedAuthority("ROLE_USER"));
        var day = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        "/api/v1/admin/queues/statistics")
                                .param("dateFrom", day)
                                .param("dateTo", day)
                                .with(user))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isForbidden());
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        "/api/v1/admin/queues/statistics")
                                .param("dateFrom", day)
                                .param("dateTo", day)
                                .with(admin))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.data[0].completedCount")
                                .value(1))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.data[0].meanWaitSeconds")
                                .value(60.0));
        stateSync.enqueue(91, 90, 1090, "SERVING");
        jdbc.update("UPDATE queue_state_sync SET status='NEEDS_ATTENTION' WHERE id=91");
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/v1/admin/queues/syncs/91/retry")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"服务身份已恢复\"}")
                                .with(admin))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk());
        assertEquals(
                "PENDING",
                jdbc.queryForObject(
                        "SELECT status FROM queue_state_sync WHERE id=91", String.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM queue_sync_audit WHERE sync_id=91", Integer.class));
    }

    @Test
    void duplicateCallUnauthorizedWindowAndInvalidTransitions() {
        ticket(3, 0);
        assertThrows(
                BusinessException.class, () -> workbench.callNext(11, 999, null, "not-open", "r0"));
        long session = Long.parseLong(workbench.start(11, 101, "open", "r1").id());
        assertThrows(
                BusinessException.class, () -> workbench.callNext(12, session, null, "bad", "r2"));
        var called = workbench.callNext(11, session, null, "once", "r3");
        assertEquals(called.id(), workbench.callNext(11, session, null, "once", "r4").id());
        assertThrows(
                BusinessException.class,
                () -> workbench.callNext(11, session, null, "twice", "r5"));
        assertThrows(
                BusinessException.class,
                () -> workbench.change(11, session, 3, 1, "COMPLETE", null, "invalid", "r6"));
        assertThrows(BusinessException.class, () -> workbench.end(11, session, 0, "end", "r7"));
        workbench.change(11, session, 3, 1, "MISS", null, "miss", "r8");
        assertThrows(
                BusinessException.class,
                () -> workbench.change(11, session, 3, 2, "START", null, "requeue", "r9"));
        assertEquals(
                "MISSED",
                jdbc.queryForObject("SELECT status FROM queue_ticket WHERE id=3", String.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM queue_state_sync WHERE target_status='MISSED'",
                        Integer.class));
        assertEquals("ENDED", workbench.end(11, session, 0, "end", "r10").status());
        assertThrows(
                BusinessException.class,
                () -> workbench.callNext(11, session, null, "closed", "r11"));
    }

    @Test
    void recallServingCompletionAndDuplicateCompletion() {
        ticket(4, 0);
        long session = Long.parseLong(workbench.start(11, 101, "start", "r1").id());
        var called = workbench.callNext(11, session, null, "call", "r2");
        assertEquals("CALLED", called.status());
        var recalled = workbench.change(11, session, 4, 1, "RECALL", null, "recall", "r3");
        assertEquals(2, recalled.callCount());
        var serving = workbench.change(11, session, 4, 2, "START", null, "serve", "r4");
        assertEquals("SERVING", serving.status());
        var done = workbench.change(11, session, 4, 3, "COMPLETE", "DONE", "complete", "r5");
        assertEquals("COMPLETED", done.status());
        assertEquals(
                done.id(),
                workbench.change(11, session, 4, 3, "COMPLETE", "DONE", "complete", "r6").id());
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM queue_state_sync WHERE ticket_id=4", Integer.class));
        assertEquals("SERVING", stateSync.due(10).get(0).targetStatus());
        long syncId = stateSync.due(10).get(0).id();
        assertEquals(
                1, stateSync.claim(syncId, "test-owner", java.time.Instant.now().plusSeconds(30)));
        stateSync.done(syncId, "test-owner");
        assertEquals("COMPLETED", stateSync.due(10).get(0).targetStatus());
        assertEquals(
                4,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM queue_operation_log WHERE ticket_id=4",
                        Integer.class));
        var overview =
                (java.util.Map<?, ?>)
                        workbench.overview(201, LocalDate.now(ZoneId.of("Asia/Shanghai")));
        assertEquals(1, ((java.util.List<?>) overview.get("groups")).size());
    }
}
