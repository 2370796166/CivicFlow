package com.civicflow.resource;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.civicflow.common.exception.BusinessException;
import com.civicflow.resource.dto.request.CreateItemRequest;
import com.civicflow.resource.dto.request.UpdateItemRequest;
import com.civicflow.resource.dto.response.ItemResponse;
import com.civicflow.resource.service.ResourceAdminService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class ResourceFlowIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ResourceAdminService adminService;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.update("DELETE FROM resource_slot_outbox");
        jdbcTemplate.update("DELETE FROM resource_slot_batch_result");
        jdbcTemplate.update("DELETE FROM resource_slot_day_lock");
        jdbcTemplate.update("DELETE FROM resource_admin_audit");
        jdbcTemplate.update("DELETE FROM resource_admin_idempotency");
        jdbcTemplate.update("DELETE FROM resource_slot");
        jdbcTemplate.update("DELETE FROM staff_window_scope");
        jdbcTemplate.update("DELETE FROM window_item_rel");
        jdbcTemplate.update("DELETE FROM service_window");
        jdbcTemplate.update("DELETE FROM service_item");
        jdbcTemplate.update("DELETE FROM service_outlet");
    }

    @Test
    void enforcesRbacValidationUniquenessIdempotencyAndVersion() throws Exception {
        String body = outletJson("OUTLET_A", "Citizen Center");
        mockMvc.perform(
                        post("/api/v1/admin/outlets")
                                .with(role("USER", 10))
                                .header("Idempotency-Key", "forbidden")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403_FORBIDDEN"));

        mockMvc.perform(
                        post("/api/v1/admin/outlets")
                                .with(role("ADMIN", 1))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_IDEMPOTENCY_REQUIRED"));

        String outletId = createOutlet("create-outlet-a", body);
        mockMvc.perform(
                        post("/api/v1/admin/outlets")
                                .with(role("ADMIN", 1))
                                .header("Idempotency-Key", "create-outlet-a")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(outletId));

        mockMvc.perform(
                        post("/api/v1/admin/outlets")
                                .with(role("ADMIN", 1))
                                .header("Idempotency-Key", "create-outlet-a")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(outletJson("OUTLET_B", "Different")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_409_IDEMPOTENCY_CONFLICT"));

        mockMvc.perform(
                        post("/api/v1/admin/outlets")
                                .with(role("ADMIN", 1))
                                .header("Idempotency-Key", "duplicate-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_409_CODE_EXISTS"));

        mockMvc.perform(
                        put("/api/v1/admin/outlets/{id}", outletId)
                                .with(role("ADMIN", 1))
                                .header("Idempotency-Key", "stale-outlet")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"code\":\"OUTLET_A\",\"name\":\"Changed\","
                                                + "\"address\":\"Road 1\",\"version\":9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_409_VERSION_CONFLICT"));

        Integer auditCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM resource_admin_audit WHERE resource_id = ?",
                        Integer.class,
                        Long.parseLong(outletId));
        assertEquals(1, auditCount);
    }

    @Test
    void exposesOnlyEnabledResourcesAndAuthorizedStaffScopes() throws Exception {
        String outletId = createOutlet("outlet-flow", outletJson("OUTLET_FLOW", "Flow Hall"));
        String itemId = createItem("item-flow", "ITEM_FLOW", "Permit");
        String windowId = createWindow("window-flow", outletId, "W01", "Window One");

        mockMvc.perform(
                        put("/api/v1/admin/windows/{id}/items", windowId)
                                .with(role("ADMIN", 1))
                                .header("Idempotency-Key", "bind-flow")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"itemIds\":[\"" + itemId + "\"],\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(get("/api/v1/user/outlets").with(role("USER", 20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].maskedContactPhone").value("*******8000"));
        mockMvc.perform(get("/api/v1/user/outlets/{id}/items", outletId).with(role("USER", 20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(itemId));

        jdbcTemplate.update(
                "INSERT INTO staff_window_scope"
                        + " (id,staff_user_id,outlet_id,window_id,deleted) VALUES (?,?,?,?,0)",
                700L,
                30L,
                Long.parseLong(outletId),
                Long.parseLong(windowId));
        mockMvc.perform(get("/api/v1/staff/scopes").with(role("STAFF", 30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].windowId").value(windowId))
                .andExpect(jsonPath("$.data[0].items[0].id").value(itemId));
        mockMvc.perform(get("/api/v1/staff/scopes").with(role("USER", 30)))
                .andExpect(status().isForbidden());

        insertFutureSlot(outletId, itemId);
        mockMvc.perform(
                        patch("/api/v1/admin/items/{id}/status", itemId)
                                .with(role("ADMIN", 1))
                                .header("Idempotency-Key", "disable-in-use-item")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"DISABLED\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_409_IN_USE"));
    }

    @Test
    void concurrentUpdatesAllowExactlyOneVersionWinner() throws Exception {
        ItemResponse created =
                adminService.createItem(
                        1L,
                        "concurrent-create",
                        "request-create",
                        new CreateItemRequest("CONCURRENT", "Original", null, 30));
        long itemId = Long.parseLong(created.id());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first =
                    executor.submit(() -> updateConcurrently(start, itemId, "update-a", "Name A"));
            Future<String> second =
                    executor.submit(() -> updateConcurrently(start, itemId, "update-b", "Name B"));
            start.countDown();
            List<String> results = List.of(first.get(), second.get());
            assertEquals(1, results.stream().filter("OK"::equals).count());
            assertEquals(
                    1, results.stream().filter("RESOURCE_409_VERSION_CONFLICT"::equals).count());
        } finally {
            executor.shutdownNow();
        }
    }

    private String updateConcurrently(CountDownLatch start, long itemId, String key, String name)
            throws Exception {
        start.await();
        try {
            adminService.updateItem(
                    1L,
                    itemId,
                    key,
                    "request-" + key,
                    new UpdateItemRequest("CONCURRENT", name, null, 30, 0));
            return "OK";
        } catch (BusinessException exception) {
            return exception.errorCode().code();
        }
    }

    private String createOutlet(String key, String body) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/admin/outlets")
                                        .with(role("ADMIN", 1))
                                        .header("Idempotency-Key", key)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.id", notNullValue()))
                        .andReturn();
        return json(result).at("/data/id").asText();
    }

    private String createItem(String key, String code, String name) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/admin/items")
                                        .with(role("ADMIN", 1))
                                        .header("Idempotency-Key", key)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"code\":\""
                                                        + code
                                                        + "\",\"name\":\""
                                                        + name
                                                        + "\",\"defaultDurationMinutes\":30}"))
                        .andExpect(status().isOk())
                        .andReturn();
        return json(result).at("/data/id").asText();
    }

    private String createWindow(String key, String outletId, String code, String name)
            throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/admin/windows")
                                        .with(role("ADMIN", 1))
                                        .header("Idempotency-Key", key)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"outletId\":\""
                                                        + outletId
                                                        + "\",\"code\":\""
                                                        + code
                                                        + "\",\"name\":\""
                                                        + name
                                                        + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        return json(result).at("/data/id").asText();
    }

    private void insertFutureSlot(String outletId, String itemId) {
        LocalDate date = LocalDate.now().plusDays(10);
        jdbcTemplate.update(
                "INSERT INTO resource_slot"
                        + " (id,outlet_id,item_id,service_date,start_time,end_time,total_quota,"
                        + "release_at,check_in_start,check_in_end,status,config_version,version,"
                        + "created_by,updated_by,deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                800L,
                Long.parseLong(outletId),
                Long.parseLong(itemId),
                date,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                20,
                LocalDateTime.now().minusDays(1),
                date.atTime(8, 45),
                date.atTime(10, 15),
                "SCHEDULED",
                1L,
                0,
                1L,
                1L);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static RequestPostProcessor role(String role, long subject) {
        return jwt().jwt(jwt -> jwt.subject(Long.toString(subject)).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static String outletJson(String code, String name) {
        return "{\"code\":\""
                + code
                + "\",\"name\":\""
                + name
                + "\",\"address\":\"Road 1\",\"contactPhone\":\"13800008000\"}";
    }
}
