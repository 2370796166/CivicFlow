package com.civicflow.resource.service.impl;

import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.api.PageResponse;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.resource.config.ContactPhoneProtector;
import com.civicflow.resource.convert.ResourceConverter;
import com.civicflow.resource.dto.response.ItemResponse;
import com.civicflow.resource.dto.response.ItemSummaryResponse;
import com.civicflow.resource.dto.response.OutletResponse;
import com.civicflow.resource.dto.response.StaffScopeResponse;
import com.civicflow.resource.dto.response.StaffScopeRow;
import com.civicflow.resource.entity.ServiceOutletEntity;
import com.civicflow.resource.enums.ResourceStatus;
import com.civicflow.resource.mapper.ServiceItemMapper;
import com.civicflow.resource.mapper.ServiceOutletMapper;
import com.civicflow.resource.mapper.StaffWindowScopeMapper;
import com.civicflow.resource.service.ResourceQueryService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ResourceQueryServiceImpl implements ResourceQueryService {
    private final ServiceOutletMapper outletMapper;
    private final ServiceItemMapper itemMapper;
    private final StaffWindowScopeMapper scopeMapper;
    private final ContactPhoneProtector contactPhoneProtector;

    public ResourceQueryServiceImpl(
            ServiceOutletMapper outletMapper,
            ServiceItemMapper itemMapper,
            StaffWindowScopeMapper scopeMapper,
            ContactPhoneProtector contactPhoneProtector) {
        this.outletMapper = outletMapper;
        this.itemMapper = itemMapper;
        this.scopeMapper = scopeMapper;
        this.contactPhoneProtector = contactPhoneProtector;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OutletResponse> listAvailableOutlets(String keyword, int page, int size) {
        String normalized = StringUtils.hasText(keyword) ? keyword.trim() : null;
        long total = outletMapper.countUserPage(normalized);
        List<OutletResponse> items =
                outletMapper.selectUserPage(normalized, (long) (page - 1) * size, size).stream()
                        .map(entity -> ResourceConverter.toOutlet(entity, contactPhoneProtector))
                        .toList();
        return PageResponse.of(items, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public OutletResponse getAvailableOutlet(long outletId) {
        ServiceOutletEntity outlet = outletMapper.selectActiveById(outletId);
        if (outlet == null || outlet.getStatus() != ResourceStatus.ENABLED) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return ResourceConverter.toOutlet(outlet, contactPhoneProtector);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> listAvailableItems(long outletId, int page, int size) {
        getAvailableOutlet(outletId);
        long total = itemMapper.countEnabledByOutlet(outletId);
        List<ItemResponse> items =
                itemMapper
                        .selectEnabledByOutletPage(outletId, (long) (page - 1) * size, size)
                        .stream()
                        .map(ResourceConverter::toItem)
                        .toList();
        return PageResponse.of(items, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffScopeResponse> listStaffScopes(long staffUserId) {
        Map<String, MutableScope> grouped = new LinkedHashMap<>();
        for (StaffScopeRow row : scopeMapper.selectAuthorizedRows(staffUserId)) {
            String key = row.outletId() + ":" + row.windowId();
            MutableScope scope =
                    grouped.computeIfAbsent(
                            key,
                            ignored ->
                                    new MutableScope(
                                            row.outletId(),
                                            row.outletCode(),
                                            row.outletName(),
                                            row.windowId(),
                                            row.windowCode(),
                                            row.windowName()));
            if (row.itemId() != null) {
                scope.items.add(
                        new ItemSummaryResponse(
                                row.itemId().toString(), row.itemCode(), row.itemName()));
            }
        }
        return grouped.values().stream().map(MutableScope::toResponse).toList();
    }

    private static final class MutableScope {
        private final Long outletId;
        private final String outletCode;
        private final String outletName;
        private final Long windowId;
        private final String windowCode;
        private final String windowName;
        private final List<ItemSummaryResponse> items = new ArrayList<>();

        private MutableScope(
                Long outletId,
                String outletCode,
                String outletName,
                Long windowId,
                String windowCode,
                String windowName) {
            this.outletId = outletId;
            this.outletCode = outletCode;
            this.outletName = outletName;
            this.windowId = windowId;
            this.windowCode = windowCode;
            this.windowName = windowName;
        }

        private StaffScopeResponse toResponse() {
            return new StaffScopeResponse(
                    outletId.toString(),
                    outletCode,
                    outletName,
                    windowId.toString(),
                    windowCode,
                    windowName,
                    items);
        }
    }
}
