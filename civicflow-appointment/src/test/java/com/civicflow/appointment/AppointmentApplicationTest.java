package com.civicflow.appointment;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.civicflow.appointment.service.StockPreheatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class AppointmentApplicationTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private StockPreheatService stockPreheatService;

    @Test
    void startsAndExposesHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void singleSlotPreheatRequiresAdmin() throws Exception {
        when(stockPreheatService.preheatOne(
                        org.mockito.ArgumentMatchers.eq(42L),
                        org.mockito.ArgumentMatchers.eq(7L),
                        anyString()))
                .thenReturn(new StockPreheatService.PreheatResult("42", "UNCHANGED", 3, 1));

        mockMvc.perform(post("/api/v1/admin/stock/slots/42/preheat"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/stock/slots/42/preheat").with(jwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        post("/api/v1/admin/stock/slots/42/preheat")
                                .header("X-Request-Id", "admin-preheat")
                                .with(
                                        jwt().jwt(jwt -> jwt.subject("7"))
                                                .authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slotId").value("42"));
    }
}
