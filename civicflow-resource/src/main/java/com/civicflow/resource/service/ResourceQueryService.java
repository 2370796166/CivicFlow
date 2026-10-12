package com.civicflow.resource.service;

import com.civicflow.common.api.PageResponse;
import com.civicflow.resource.dto.response.ItemResponse;
import com.civicflow.resource.dto.response.OutletResponse;
import com.civicflow.resource.dto.response.StaffScopeResponse;
import com.civicflow.resource.dto.response.UserSlotResponse;
import java.time.LocalDate;
import java.util.List;

public interface ResourceQueryService {
    PageResponse<OutletResponse> listAvailableOutlets(String keyword, int page, int size);

    OutletResponse getAvailableOutlet(long outletId);

    PageResponse<ItemResponse> listAvailableItems(long outletId, int page, int size);

    List<StaffScopeResponse> listStaffScopes(long staffUserId);

    PageResponse<UserSlotResponse> listAvailableSlots(
            long outletId, long itemId, LocalDate serviceDate, int page, int size);
}
