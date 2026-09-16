package com.civicflow.resource.service;

import com.civicflow.common.api.PageResponse;
import com.civicflow.resource.dto.request.ChangeResourceStatusRequest;
import com.civicflow.resource.dto.request.CreateItemRequest;
import com.civicflow.resource.dto.request.CreateOutletRequest;
import com.civicflow.resource.dto.request.CreateWindowRequest;
import com.civicflow.resource.dto.request.ReplaceWindowItemsRequest;
import com.civicflow.resource.dto.request.UpdateItemRequest;
import com.civicflow.resource.dto.request.UpdateOutletRequest;
import com.civicflow.resource.dto.request.UpdateWindowRequest;
import com.civicflow.resource.dto.response.ItemResponse;
import com.civicflow.resource.dto.response.OutletResponse;
import com.civicflow.resource.dto.response.WindowItemsResponse;
import com.civicflow.resource.dto.response.WindowResponse;
import com.civicflow.resource.enums.ResourceStatus;

public interface ResourceAdminService {
    PageResponse<OutletResponse> listOutlets(
            String keyword, ResourceStatus status, int page, int size);

    OutletResponse getOutlet(long id);

    OutletResponse createOutlet(
            long actorId, String idempotencyKey, String requestId, CreateOutletRequest request);

    OutletResponse updateOutlet(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            UpdateOutletRequest request);

    OutletResponse changeOutletStatus(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            ChangeResourceStatusRequest request);

    void deleteOutlet(long actorId, long id, int version, String idempotencyKey, String requestId);

    PageResponse<ItemResponse> listItems(String keyword, ResourceStatus status, int page, int size);

    ItemResponse getItem(long id);

    ItemResponse createItem(
            long actorId, String idempotencyKey, String requestId, CreateItemRequest request);

    ItemResponse updateItem(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            UpdateItemRequest request);

    ItemResponse changeItemStatus(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            ChangeResourceStatusRequest request);

    void deleteItem(long actorId, long id, int version, String idempotencyKey, String requestId);

    PageResponse<WindowResponse> listWindows(
            Long outletId, String keyword, ResourceStatus status, int page, int size);

    WindowResponse getWindow(long id);

    WindowResponse createWindow(
            long actorId, String idempotencyKey, String requestId, CreateWindowRequest request);

    WindowResponse updateWindow(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            UpdateWindowRequest request);

    WindowResponse changeWindowStatus(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            ChangeResourceStatusRequest request);

    void deleteWindow(long actorId, long id, int version, String idempotencyKey, String requestId);

    WindowItemsResponse replaceWindowItems(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            ReplaceWindowItemsRequest request);
}
