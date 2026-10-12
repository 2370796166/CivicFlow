package com.civicflow.appointment.controller;

import com.civicflow.appointment.config.RequestIdFilter;
import com.civicflow.appointment.service.SlotAvailabilityService;
import com.civicflow.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/user/slots")
@PreAuthorize("hasRole('USER')")
public class UserSlotController {
    private final SlotAvailabilityService slots;

    public UserSlotController(SlotAvailabilityService slots) {
        this.slots = slots;
    }

    @GetMapping
    ApiResponse<List<SlotAvailabilityService.Slot>> list(
            @RequestParam @Positive long outletId,
            @RequestParam @Positive long itemId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {
        return ApiResponse.success(
                slots.list(outletId, itemId, date), RequestIdFilter.current(request));
    }
}
