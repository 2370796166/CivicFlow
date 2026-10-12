package com.civicflow.resource.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.api.PageResponse;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.resource.config.ContactPhoneProtector;
import com.civicflow.resource.convert.ResourceConverter;
import com.civicflow.resource.dto.request.ChangeResourceStatusRequest;
import com.civicflow.resource.dto.request.CreateItemRequest;
import com.civicflow.resource.dto.request.CreateOutletRequest;
import com.civicflow.resource.dto.request.CreateWindowRequest;
import com.civicflow.resource.dto.request.ReplaceWindowItemsRequest;
import com.civicflow.resource.dto.request.ReplaceWindowStaffRequest;
import com.civicflow.resource.dto.request.UpdateItemRequest;
import com.civicflow.resource.dto.request.UpdateOutletRequest;
import com.civicflow.resource.dto.request.UpdateWindowRequest;
import com.civicflow.resource.dto.response.ItemResponse;
import com.civicflow.resource.dto.response.OutletResponse;
import com.civicflow.resource.dto.response.WindowItemsResponse;
import com.civicflow.resource.dto.response.WindowResponse;
import com.civicflow.resource.dto.response.WindowStaffResponse;
import com.civicflow.resource.entity.ResourceAdminAuditEntity;
import com.civicflow.resource.entity.ResourceAdminIdempotencyEntity;
import com.civicflow.resource.entity.ServiceItemEntity;
import com.civicflow.resource.entity.ServiceOutletEntity;
import com.civicflow.resource.entity.ServiceWindowEntity;
import com.civicflow.resource.entity.StaffWindowScopeEntity;
import com.civicflow.resource.entity.WindowItemRelEntity;
import com.civicflow.resource.enums.ResourceAdminOperation;
import com.civicflow.resource.enums.ResourceStatus;
import com.civicflow.resource.error.ResourceErrorCode;
import com.civicflow.resource.mapper.ResourceAdminAuditMapper;
import com.civicflow.resource.mapper.ResourceAdminIdempotencyMapper;
import com.civicflow.resource.mapper.ResourceSlotMapper;
import com.civicflow.resource.mapper.ServiceItemMapper;
import com.civicflow.resource.mapper.ServiceOutletMapper;
import com.civicflow.resource.mapper.ServiceWindowMapper;
import com.civicflow.resource.mapper.StaffWindowScopeMapper;
import com.civicflow.resource.mapper.WindowItemRelMapper;
import com.civicflow.resource.service.ResourceAdminService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ResourceAdminServiceImpl implements ResourceAdminService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ServiceOutletMapper outletMapper;
    private final ServiceItemMapper itemMapper;
    private final ServiceWindowMapper windowMapper;
    private final WindowItemRelMapper windowItemMapper;
    private final StaffWindowScopeMapper scopeMapper;
    private final ResourceSlotMapper slotMapper;
    private final ResourceAdminIdempotencyMapper idempotencyMapper;
    private final ResourceAdminAuditMapper auditMapper;
    private final ContactPhoneProtector contactPhoneProtector;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public ResourceAdminServiceImpl(
            ServiceOutletMapper outletMapper,
            ServiceItemMapper itemMapper,
            ServiceWindowMapper windowMapper,
            WindowItemRelMapper windowItemMapper,
            StaffWindowScopeMapper scopeMapper,
            ResourceSlotMapper slotMapper,
            ResourceAdminIdempotencyMapper idempotencyMapper,
            ResourceAdminAuditMapper auditMapper,
            ContactPhoneProtector contactPhoneProtector,
            Clock clock,
            ObjectMapper objectMapper) {
        this.outletMapper = outletMapper;
        this.itemMapper = itemMapper;
        this.windowMapper = windowMapper;
        this.windowItemMapper = windowItemMapper;
        this.scopeMapper = scopeMapper;
        this.slotMapper = slotMapper;
        this.idempotencyMapper = idempotencyMapper;
        this.auditMapper = auditMapper;
        this.contactPhoneProtector = contactPhoneProtector;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OutletResponse> listOutlets(
            String keyword, ResourceStatus status, int page, int size) {
        String normalized = normalizeKeyword(keyword);
        long total = outletMapper.countAdminPage(normalized, status);
        List<OutletResponse> items =
                outletMapper.selectAdminPage(normalized, status, offset(page, size), size).stream()
                        .map(entity -> ResourceConverter.toOutlet(entity, contactPhoneProtector))
                        .toList();
        return PageResponse.of(items, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public OutletResponse getOutlet(long id) {
        return outletView(requireOutlet(id));
    }

    @Override
    @Transactional
    public OutletResponse createOutlet(
            long actorId, String idempotencyKey, String requestId, CreateOutletRequest request) {
        String phone = normalizePhone(request.contactPhone());
        String canonical =
                normalizedCode(request.code())
                        + "|"
                        + request.name().trim()
                        + "|"
                        + request.address().trim()
                        + "|"
                        + value(request.longitude())
                        + "|"
                        + value(request.latitude())
                        + "|"
                        + value(phone);
        ResourceAdminIdempotencyEntity operation =
                reserve(actorId, ResourceAdminOperation.CREATE_OUTLET, idempotencyKey, canonical);
        if (operation.getResourceId() != null) {
            return outletView(requireOutlet(operation.getResourceId()));
        }
        ServiceOutletEntity entity = new ServiceOutletEntity();
        entity.setCode(normalizedCode(request.code()));
        entity.setName(request.name().trim());
        entity.setAddress(request.address().trim());
        entity.setLongitude(request.longitude());
        entity.setLatitude(request.latitude());
        applyContact(entity, phone);
        entity.setStatus(ResourceStatus.ENABLED);
        entity.setVersion(0);
        try {
            outletMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResourceErrorCode.CODE_EXISTS, exception);
        }
        bind(operation, entity.getId());
        audit(
                actorId,
                "OUTLET",
                entity.getId(),
                operation.getOperation(),
                requestId,
                null,
                outletSnapshot(entity));
        return outletView(entity);
    }

    @Override
    @Transactional
    public OutletResponse updateOutlet(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            UpdateOutletRequest request) {
        String phone = normalizePhone(request.contactPhone());
        String canonical =
                id
                        + "|"
                        + normalizedCode(request.code())
                        + "|"
                        + request.name().trim()
                        + "|"
                        + request.address().trim()
                        + "|"
                        + value(request.longitude())
                        + "|"
                        + value(request.latitude())
                        + "|"
                        + value(phone)
                        + "|"
                        + request.version();
        ResourceAdminIdempotencyEntity operation =
                reserve(actorId, ResourceAdminOperation.UPDATE_OUTLET, idempotencyKey, canonical);
        if (operation.getResourceId() != null) {
            return outletView(requireOutlet(operation.getResourceId()));
        }
        ServiceOutletEntity current = requireOutletForUpdate(id, request.version());
        byte[] cipher = phone == null ? null : contactPhoneProtector.encrypt(phone);
        Integer keyVersion = phone == null ? null : contactPhoneProtector.keyVersion();
        try {
            requireUpdated(
                    outletMapper.updateDetails(
                            id,
                            normalizedCode(request.code()),
                            request.name().trim(),
                            request.address().trim(),
                            request.longitude(),
                            request.latitude(),
                            cipher,
                            keyVersion,
                            request.version()));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResourceErrorCode.CODE_EXISTS, exception);
        }
        bind(operation, id);
        ServiceOutletEntity updated = requireOutlet(id);
        audit(
                actorId,
                "OUTLET",
                id,
                operation.getOperation(),
                requestId,
                outletSnapshot(current),
                outletSnapshot(updated));
        return outletView(updated);
    }

    @Override
    @Transactional
    public OutletResponse changeOutletStatus(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            ChangeResourceStatusRequest request) {
        ResourceAdminIdempotencyEntity operation =
                reserve(
                        actorId,
                        ResourceAdminOperation.CHANGE_OUTLET_STATUS,
                        idempotencyKey,
                        id + "|" + request.status() + "|" + request.version());
        if (operation.getResourceId() != null) {
            return outletView(requireOutlet(operation.getResourceId()));
        }
        ServiceOutletEntity current = requireOutletForUpdate(id, request.version());
        if (current.getStatus() != request.status()) {
            if (request.status() == ResourceStatus.DISABLED
                    && slotMapper.countFutureByOutlet(id, businessDate()) > 0) {
                throw new BusinessException(ResourceErrorCode.IN_USE);
            }
            requireUpdated(outletMapper.updateStatus(id, request.status(), request.version()));
        }
        bind(operation, id);
        ServiceOutletEntity updated = requireOutlet(id);
        audit(
                actorId,
                "OUTLET",
                id,
                operation.getOperation(),
                requestId,
                outletSnapshot(current),
                outletSnapshot(updated));
        return outletView(updated);
    }

    @Override
    @Transactional
    public void deleteOutlet(
            long actorId, long id, int version, String idempotencyKey, String requestId) {
        ResourceAdminIdempotencyEntity operation =
                reserve(
                        actorId,
                        ResourceAdminOperation.DELETE_OUTLET,
                        idempotencyKey,
                        id + "|" + version);
        if (operation.getResourceId() != null) {
            return;
        }
        ServiceOutletEntity current = requireOutletForUpdate(id, version);
        if (slotMapper.countFutureByOutlet(id, businessDate()) > 0
                || windowMapper.countActiveByOutlet(id) > 0
                || scopeMapper.countByOutlet(id) > 0) {
            throw new BusinessException(ResourceErrorCode.IN_USE);
        }
        requireUpdated(outletMapper.logicallyDelete(id, version));
        bind(operation, id);
        audit(
                actorId,
                "OUTLET",
                id,
                operation.getOperation(),
                requestId,
                outletSnapshot(current),
                Map.of("deleted", true));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> listItems(
            String keyword, ResourceStatus status, int page, int size) {
        String normalized = normalizeKeyword(keyword);
        long total = itemMapper.countAdminPage(normalized, status);
        List<ItemResponse> items =
                itemMapper.selectAdminPage(normalized, status, offset(page, size), size).stream()
                        .map(ResourceConverter::toItem)
                        .toList();
        return PageResponse.of(items, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public ItemResponse getItem(long id) {
        return ResourceConverter.toItem(requireItem(id));
    }

    @Override
    @Transactional
    public ItemResponse createItem(
            long actorId, String idempotencyKey, String requestId, CreateItemRequest request) {
        String canonical =
                normalizedCode(request.code())
                        + "|"
                        + request.name().trim()
                        + "|"
                        + normalizeNullable(request.description())
                        + "|"
                        + request.defaultDurationMinutes();
        ResourceAdminIdempotencyEntity operation =
                reserve(actorId, ResourceAdminOperation.CREATE_ITEM, idempotencyKey, canonical);
        if (operation.getResourceId() != null) {
            return ResourceConverter.toItem(requireItem(operation.getResourceId()));
        }
        ServiceItemEntity entity = new ServiceItemEntity();
        entity.setCode(normalizedCode(request.code()));
        entity.setName(request.name().trim());
        entity.setDescription(normalizeNullable(request.description()));
        entity.setDefaultDurationMinutes(request.defaultDurationMinutes());
        entity.setStatus(ResourceStatus.ENABLED);
        entity.setVersion(0);
        try {
            itemMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResourceErrorCode.CODE_EXISTS, exception);
        }
        bind(operation, entity.getId());
        audit(
                actorId,
                "ITEM",
                entity.getId(),
                operation.getOperation(),
                requestId,
                null,
                itemSnapshot(entity));
        return ResourceConverter.toItem(entity);
    }

    @Override
    @Transactional
    public ItemResponse updateItem(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            UpdateItemRequest request) {
        String canonical =
                id
                        + "|"
                        + normalizedCode(request.code())
                        + "|"
                        + request.name().trim()
                        + "|"
                        + normalizeNullable(request.description())
                        + "|"
                        + request.defaultDurationMinutes()
                        + "|"
                        + request.version();
        ResourceAdminIdempotencyEntity operation =
                reserve(actorId, ResourceAdminOperation.UPDATE_ITEM, idempotencyKey, canonical);
        if (operation.getResourceId() != null) {
            return ResourceConverter.toItem(requireItem(operation.getResourceId()));
        }
        ServiceItemEntity current = requireItemForUpdate(id, request.version());
        try {
            requireUpdated(
                    itemMapper.updateDetails(
                            id,
                            normalizedCode(request.code()),
                            request.name().trim(),
                            normalizeNullable(request.description()),
                            request.defaultDurationMinutes(),
                            request.version()));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResourceErrorCode.CODE_EXISTS, exception);
        }
        bind(operation, id);
        ServiceItemEntity updated = requireItem(id);
        audit(
                actorId,
                "ITEM",
                id,
                operation.getOperation(),
                requestId,
                itemSnapshot(current),
                itemSnapshot(updated));
        return ResourceConverter.toItem(updated);
    }

    @Override
    @Transactional
    public ItemResponse changeItemStatus(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            ChangeResourceStatusRequest request) {
        ResourceAdminIdempotencyEntity operation =
                reserve(
                        actorId,
                        ResourceAdminOperation.CHANGE_ITEM_STATUS,
                        idempotencyKey,
                        id + "|" + request.status() + "|" + request.version());
        if (operation.getResourceId() != null) {
            return ResourceConverter.toItem(requireItem(operation.getResourceId()));
        }
        ServiceItemEntity current = requireItemForUpdate(id, request.version());
        if (current.getStatus() != request.status()) {
            if (request.status() == ResourceStatus.DISABLED
                    && slotMapper.countFutureByItem(id, businessDate()) > 0) {
                throw new BusinessException(ResourceErrorCode.IN_USE);
            }
            requireUpdated(itemMapper.updateStatus(id, request.status(), request.version()));
        }
        bind(operation, id);
        ServiceItemEntity updated = requireItem(id);
        audit(
                actorId,
                "ITEM",
                id,
                operation.getOperation(),
                requestId,
                itemSnapshot(current),
                itemSnapshot(updated));
        return ResourceConverter.toItem(updated);
    }

    @Override
    @Transactional
    public void deleteItem(
            long actorId, long id, int version, String idempotencyKey, String requestId) {
        ResourceAdminIdempotencyEntity operation =
                reserve(
                        actorId,
                        ResourceAdminOperation.DELETE_ITEM,
                        idempotencyKey,
                        id + "|" + version);
        if (operation.getResourceId() != null) {
            return;
        }
        ServiceItemEntity current = requireItemForUpdate(id, version);
        if (slotMapper.countFutureByItem(id, businessDate()) > 0
                || windowItemMapper.countByItem(id) > 0) {
            throw new BusinessException(ResourceErrorCode.IN_USE);
        }
        requireUpdated(itemMapper.logicallyDelete(id, version));
        bind(operation, id);
        audit(
                actorId,
                "ITEM",
                id,
                operation.getOperation(),
                requestId,
                itemSnapshot(current),
                Map.of("deleted", true));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WindowResponse> listWindows(
            Long outletId, String keyword, ResourceStatus status, int page, int size) {
        String normalized = normalizeKeyword(keyword);
        long total = windowMapper.countAdminPage(outletId, normalized, status);
        List<WindowResponse> items =
                windowMapper
                        .selectAdminPage(outletId, normalized, status, offset(page, size), size)
                        .stream()
                        .map(ResourceConverter::toWindow)
                        .toList();
        return PageResponse.of(items, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public WindowResponse getWindow(long id) {
        return ResourceConverter.toWindow(requireWindow(id));
    }

    @Override
    @Transactional
    public WindowResponse createWindow(
            long actorId, String idempotencyKey, String requestId, CreateWindowRequest request) {
        long outletId = parseId(request.outletId());
        String canonical =
                outletId + "|" + normalizedCode(request.code()) + "|" + request.name().trim();
        ResourceAdminIdempotencyEntity operation =
                reserve(actorId, ResourceAdminOperation.CREATE_WINDOW, idempotencyKey, canonical);
        if (operation.getResourceId() != null) {
            return ResourceConverter.toWindow(requireWindow(operation.getResourceId()));
        }
        requireOutlet(outletId);
        ServiceWindowEntity entity = new ServiceWindowEntity();
        entity.setOutletId(outletId);
        entity.setCode(normalizedCode(request.code()));
        entity.setName(request.name().trim());
        entity.setStatus(ResourceStatus.ENABLED);
        entity.setVersion(0);
        try {
            windowMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResourceErrorCode.CODE_EXISTS, exception);
        }
        bind(operation, entity.getId());
        audit(
                actorId,
                "WINDOW",
                entity.getId(),
                operation.getOperation(),
                requestId,
                null,
                windowSnapshot(entity));
        return ResourceConverter.toWindow(entity);
    }

    @Override
    @Transactional
    public WindowResponse updateWindow(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            UpdateWindowRequest request) {
        long outletId = parseId(request.outletId());
        String canonical =
                id
                        + "|"
                        + outletId
                        + "|"
                        + normalizedCode(request.code())
                        + "|"
                        + request.name().trim()
                        + "|"
                        + request.version();
        ResourceAdminIdempotencyEntity operation =
                reserve(actorId, ResourceAdminOperation.UPDATE_WINDOW, idempotencyKey, canonical);
        if (operation.getResourceId() != null) {
            return ResourceConverter.toWindow(requireWindow(operation.getResourceId()));
        }
        ServiceWindowEntity current = requireWindowForUpdate(id, request.version());
        requireOutlet(outletId);
        if (current.getOutletId() != outletId
                && (windowItemMapper.countByWindow(id) > 0
                        || scopeMapper.countByWindow(id) > 0
                        || slotMapper.countFutureByWindow(id, businessDate()) > 0)) {
            throw new BusinessException(ResourceErrorCode.IN_USE);
        }
        try {
            requireUpdated(
                    windowMapper.updateDetails(
                            id,
                            outletId,
                            normalizedCode(request.code()),
                            request.name().trim(),
                            request.version()));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResourceErrorCode.CODE_EXISTS, exception);
        }
        bind(operation, id);
        ServiceWindowEntity updated = requireWindow(id);
        audit(
                actorId,
                "WINDOW",
                id,
                operation.getOperation(),
                requestId,
                windowSnapshot(current),
                windowSnapshot(updated));
        return ResourceConverter.toWindow(updated);
    }

    @Override
    @Transactional
    public WindowResponse changeWindowStatus(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            ChangeResourceStatusRequest request) {
        ResourceAdminIdempotencyEntity operation =
                reserve(
                        actorId,
                        ResourceAdminOperation.CHANGE_WINDOW_STATUS,
                        idempotencyKey,
                        id + "|" + request.status() + "|" + request.version());
        if (operation.getResourceId() != null) {
            return ResourceConverter.toWindow(requireWindow(operation.getResourceId()));
        }
        ServiceWindowEntity current = requireWindowForUpdate(id, request.version());
        if (current.getStatus() != request.status()) {
            if (request.status() == ResourceStatus.DISABLED
                    && slotMapper.countFutureByWindow(id, businessDate()) > 0) {
                throw new BusinessException(ResourceErrorCode.IN_USE);
            }
            requireUpdated(windowMapper.updateStatus(id, request.status(), request.version()));
        }
        bind(operation, id);
        ServiceWindowEntity updated = requireWindow(id);
        audit(
                actorId,
                "WINDOW",
                id,
                operation.getOperation(),
                requestId,
                windowSnapshot(current),
                windowSnapshot(updated));
        return ResourceConverter.toWindow(updated);
    }

    @Override
    @Transactional
    public void deleteWindow(
            long actorId, long id, int version, String idempotencyKey, String requestId) {
        ResourceAdminIdempotencyEntity operation =
                reserve(
                        actorId,
                        ResourceAdminOperation.DELETE_WINDOW,
                        idempotencyKey,
                        id + "|" + version);
        if (operation.getResourceId() != null) {
            return;
        }
        ServiceWindowEntity current = requireWindowForUpdate(id, version);
        if (windowItemMapper.countByWindow(id) > 0
                || scopeMapper.countByWindow(id) > 0
                || slotMapper.countFutureByWindow(id, businessDate()) > 0) {
            throw new BusinessException(ResourceErrorCode.IN_USE);
        }
        requireUpdated(windowMapper.logicallyDelete(id, version));
        bind(operation, id);
        audit(
                actorId,
                "WINDOW",
                id,
                operation.getOperation(),
                requestId,
                windowSnapshot(current),
                Map.of("deleted", true));
    }

    @Override
    @Transactional
    public WindowItemsResponse replaceWindowItems(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            ReplaceWindowItemsRequest request) {
        List<Long> itemIds =
                request.itemIds().stream().map(ResourceAdminServiceImpl::parseId).sorted().toList();
        String canonical =
                id
                        + "|"
                        + itemIds.stream().map(String::valueOf).collect(Collectors.joining(","))
                        + "|"
                        + request.version();
        ResourceAdminIdempotencyEntity operation =
                reserve(
                        actorId,
                        ResourceAdminOperation.REPLACE_WINDOW_ITEMS,
                        idempotencyKey,
                        canonical);
        if (operation.getResourceId() != null) {
            return windowItems(operation.getResourceId());
        }
        ServiceWindowEntity current = requireWindowForUpdate(id, request.version());
        List<Long> before = windowItemMapper.selectActiveItemIds(id);
        if (!itemIds.isEmpty() && itemMapper.selectEnabledByIds(itemIds).size() != itemIds.size()) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        requireUpdated(windowMapper.incrementVersion(id, request.version()));
        windowItemMapper.logicallyDeleteByWindow(id);
        for (Long itemId : itemIds) {
            WindowItemRelEntity relation = new WindowItemRelEntity();
            relation.setWindowId(id);
            relation.setItemId(itemId);
            windowItemMapper.insert(relation);
        }
        bind(operation, id);
        audit(
                actorId,
                "WINDOW",
                id,
                operation.getOperation(),
                requestId,
                Map.of("version", current.getVersion(), "itemIds", before),
                Map.of("version", current.getVersion() + 1, "itemIds", itemIds));
        return windowItems(id);
    }

    @Override
    @Transactional(readOnly = true)
    public WindowItemsResponse getWindowItems(long id) {
        return windowItems(id);
    }

    @Override
    @Transactional(readOnly = true)
    public WindowStaffResponse getWindowStaff(long id) {
        ServiceWindowEntity window = requireWindow(id);
        return new WindowStaffResponse(
                Long.toString(id),
                window.getVersion(),
                scopeMapper.selectDirectStaffIds(id).stream().map(String::valueOf).toList(),
                scopeMapper.selectInheritedStaffIds(window.getOutletId()).stream()
                        .map(String::valueOf)
                        .toList());
    }

    @Override
    @Transactional
    public WindowStaffResponse replaceWindowStaff(
            long actorId,
            long id,
            String idempotencyKey,
            String requestId,
            ReplaceWindowStaffRequest request) {
        List<Long> staffIds =
                request.staffUserIds().stream()
                        .map(ResourceAdminServiceImpl::parseId)
                        .sorted()
                        .toList();
        String canonical =
                id
                        + "|"
                        + staffIds.stream().map(String::valueOf).collect(Collectors.joining(","))
                        + "|"
                        + request.version();
        ResourceAdminIdempotencyEntity operation =
                reserve(
                        actorId,
                        ResourceAdminOperation.REPLACE_WINDOW_STAFF,
                        idempotencyKey,
                        canonical);
        if (operation.getResourceId() != null) return getWindowStaff(operation.getResourceId());
        ServiceWindowEntity window = requireWindowForUpdate(id, request.version());
        List<Long> before = scopeMapper.selectDirectStaffIds(id);
        requireUpdated(windowMapper.incrementVersion(id, request.version()));
        scopeMapper.logicallyDeleteByWindow(id);
        for (Long staffId : staffIds) {
            StaffWindowScopeEntity scope = new StaffWindowScopeEntity();
            scope.setStaffUserId(staffId);
            scope.setOutletId(window.getOutletId());
            scope.setWindowId(id);
            scopeMapper.insert(scope);
        }
        bind(operation, id);
        audit(
                actorId,
                "WINDOW",
                id,
                operation.getOperation(),
                requestId,
                Map.of("version", window.getVersion(), "staffUserIds", before),
                Map.of("version", window.getVersion() + 1, "staffUserIds", staffIds));
        return getWindowStaff(id);
    }

    private ResourceAdminIdempotencyEntity reserve(
            long actorId,
            ResourceAdminOperation operation,
            String idempotencyKey,
            String canonicalPayload) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_REQUIRED);
        }
        byte[] payloadHash = contactPhoneProtector.payloadHash(canonicalPayload);
        ResourceAdminIdempotencyEntity candidate = new ResourceAdminIdempotencyEntity();
        candidate.setId(IdWorker.getId());
        candidate.setActorUserId(actorId);
        candidate.setOperation(operation.name());
        candidate.setIdempotencyKey(idempotencyKey);
        candidate.setPayloadHash(payloadHash);
        candidate.setCreatedAt(clock.instant());
        candidate.setUpdatedAt(clock.instant());
        idempotencyMapper.insertIgnore(candidate);
        ResourceAdminIdempotencyEntity stored =
                idempotencyMapper.selectForUpdate(actorId, operation.name(), idempotencyKey);
        if (stored == null || !MessageDigest.isEqual(stored.getPayloadHash(), payloadHash)) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return stored;
    }

    private void bind(ResourceAdminIdempotencyEntity operation, long resourceId) {
        idempotencyMapper.bindResource(operation.getId(), resourceId);
    }

    private void audit(
            long actorId,
            String resourceType,
            long resourceId,
            String action,
            String requestId,
            Map<String, Object> before,
            Map<String, Object> after) {
        ResourceAdminAuditEntity audit = new ResourceAdminAuditEntity();
        audit.setActorUserId(actorId);
        audit.setResourceType(resourceType);
        audit.setResourceId(resourceId);
        audit.setAction(action);
        audit.setRequestId(requestId);
        audit.setBeforeJson(toJson(before));
        audit.setAfterJson(toJson(after));
        audit.setOccurredAt(clock.instant());
        auditMapper.insert(audit);
    }

    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize resource audit", exception);
        }
    }

    private ServiceOutletEntity requireOutlet(long id) {
        ServiceOutletEntity entity = outletMapper.selectActiveById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return entity;
    }

    private ServiceOutletEntity requireOutletForUpdate(long id, int version) {
        ServiceOutletEntity entity = outletMapper.selectByIdForUpdate(id);
        assertVersion(entity == null ? null : entity.getVersion(), version);
        return entity;
    }

    private ServiceItemEntity requireItem(long id) {
        ServiceItemEntity entity = itemMapper.selectActiveById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return entity;
    }

    private ServiceItemEntity requireItemForUpdate(long id, int version) {
        ServiceItemEntity entity = itemMapper.selectByIdForUpdate(id);
        assertVersion(entity == null ? null : entity.getVersion(), version);
        return entity;
    }

    private ServiceWindowEntity requireWindow(long id) {
        ServiceWindowEntity entity = windowMapper.selectActiveById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return entity;
    }

    private ServiceWindowEntity requireWindowForUpdate(long id, int version) {
        ServiceWindowEntity entity = windowMapper.selectByIdForUpdate(id);
        assertVersion(entity == null ? null : entity.getVersion(), version);
        return entity;
    }

    private static void assertVersion(Integer current, int expected) {
        if (current == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        if (current != expected) {
            throw new BusinessException(ResourceErrorCode.VERSION_CONFLICT);
        }
    }

    private static void requireUpdated(int rows) {
        if (rows != 1) {
            throw new BusinessException(ResourceErrorCode.VERSION_CONFLICT);
        }
    }

    private OutletResponse outletView(ServiceOutletEntity entity) {
        return ResourceConverter.toOutlet(entity, contactPhoneProtector);
    }

    private WindowItemsResponse windowItems(long windowId) {
        ServiceWindowEntity window = requireWindow(windowId);
        return new WindowItemsResponse(
                windowId + "",
                window.getVersion(),
                windowItemMapper.selectActiveItemIds(windowId).stream()
                        .map(String::valueOf)
                        .toList());
    }

    private void applyContact(ServiceOutletEntity entity, String phone) {
        if (phone != null) {
            entity.setContactPhoneCipher(contactPhoneProtector.encrypt(phone));
            entity.setContactPhoneKeyVersion(contactPhoneProtector.keyVersion());
        }
    }

    private String normalizePhone(String phone) {
        return StringUtils.hasText(phone) ? contactPhoneProtector.normalize(phone) : null;
    }

    private LocalDate businessDate() {
        return LocalDate.now(clock.withZone(BUSINESS_ZONE));
    }

    private static long offset(int page, int size) {
        return (long) (page - 1) * size;
    }

    private static String normalizeKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private static String normalizedCode(String code) {
        return code.trim().toUpperCase();
    }

    private static String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private static long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.VALIDATION, exception);
        }
    }

    private static Map<String, Object> outletSnapshot(ServiceOutletEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", entity.getCode());
        snapshot.put("name", entity.getName());
        snapshot.put("status", entity.getStatus().name());
        snapshot.put("version", entity.getVersion());
        return snapshot;
    }

    private static Map<String, Object> itemSnapshot(ServiceItemEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", entity.getCode());
        snapshot.put("name", entity.getName());
        snapshot.put("status", entity.getStatus().name());
        snapshot.put("version", entity.getVersion());
        return snapshot;
    }

    private static Map<String, Object> windowSnapshot(ServiceWindowEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("outletId", entity.getOutletId().toString());
        snapshot.put("code", entity.getCode());
        snapshot.put("name", entity.getName());
        snapshot.put("status", entity.getStatus().name());
        snapshot.put("version", entity.getVersion());
        return snapshot;
    }
}
