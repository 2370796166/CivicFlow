package com.civicflow.resource.service;

import com.civicflow.common.api.PageResponse;
import com.civicflow.resource.dto.request.AdjustSlotQuotaRequest;
import com.civicflow.resource.dto.request.BatchCreateSlotsRequest;
import com.civicflow.resource.dto.request.ChangeSlotStatusRequest;
import com.civicflow.resource.dto.request.CreateSlotRequest;
import com.civicflow.resource.dto.request.UpdateSlotRequest;
import com.civicflow.resource.dto.response.SlotBatchResponse;
import com.civicflow.resource.dto.response.SlotResponse;
import com.civicflow.resource.enums.SlotStatus;
import java.time.LocalDate;

public interface SlotAdminService {
    PageResponse<SlotResponse> list(
            Long outletId,
            Long itemId,
            LocalDate dateFrom,
            LocalDate dateTo,
            SlotStatus status,
            int page,
            int size);

    SlotResponse get(long id);

    SlotResponse create(
            long actorId, String idempotencyKey, String requestId, CreateSlotRequest request);

    SlotBatchResponse batchCreate(
            long actorId, String idempotencyKey, String requestId, BatchCreateSlotsRequest request);

    SlotResponse update(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            UpdateSlotRequest request);

    SlotResponse changeStatus(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            ChangeSlotStatusRequest request);

    SlotResponse adjustQuota(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            AdjustSlotQuotaRequest request);
}
