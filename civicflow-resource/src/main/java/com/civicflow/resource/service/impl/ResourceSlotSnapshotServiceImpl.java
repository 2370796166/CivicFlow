package com.civicflow.resource.service.impl;

import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.resource.dto.response.SlotCandidatePageResponse;
import com.civicflow.resource.dto.response.SlotSnapshotResponse;
import com.civicflow.resource.dto.response.SlotSnapshotRow;
import com.civicflow.resource.mapper.ResourceSlotMapper;
import com.civicflow.resource.service.ResourceSlotSnapshotService;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ResourceSlotSnapshotServiceImpl implements ResourceSlotSnapshotService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final ResourceSlotMapper mapper;

    public ResourceSlotSnapshotServiceImpl(ResourceSlotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public SlotSnapshotResponse get(long slotId) {
        SlotSnapshotRow row = mapper.selectSnapshotById(slotId);
        if (row == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return toResponse(row);
    }

    @Override
    public SlotCandidatePageResponse candidates(
            Instant releaseFrom, Instant releaseTo, long afterId, int size) {
        if (releaseFrom == null
                || releaseTo == null
                || !releaseTo.isAfter(releaseFrom)
                || afterId < 0
                || size < 1
                || size > 500) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        List<SlotSnapshotRow> rows =
                mapper.selectPreheatSnapshotCandidates(releaseFrom, releaseTo, afterId, size + 1);
        boolean hasMore = rows.size() > size;
        List<SlotSnapshotRow> page = hasMore ? rows.subList(0, size) : rows;
        String nextCursor =
                page.isEmpty()
                        ? Long.toString(afterId)
                        : page.get(page.size() - 1).slotId().toString();
        return new SlotCandidatePageResponse(
                page.stream().map(ResourceSlotSnapshotServiceImpl::toResponse).toList(),
                nextCursor,
                hasMore);
    }

    @Override
    public SlotCandidatePageResponse reconciliationCandidates(Instant at, long afterId, int size) {
        if (at == null || afterId < 0 || size < 1 || size > 100) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        var local = at.atZone(BUSINESS_ZONE);
        List<SlotSnapshotRow> rows =
                mapper.selectReconciliationCandidates(
                        at, local.toLocalDate(), local.toLocalTime(), afterId, size + 1);
        boolean hasMore = rows.size() > size;
        List<SlotSnapshotRow> page = hasMore ? rows.subList(0, size) : rows;
        String nextCursor =
                page.isEmpty()
                        ? Long.toString(afterId)
                        : page.get(page.size() - 1).slotId().toString();
        return new SlotCandidatePageResponse(
                page.stream().map(ResourceSlotSnapshotServiceImpl::toResponse).toList(),
                nextCursor,
                hasMore);
    }

    private static SlotSnapshotResponse toResponse(SlotSnapshotRow row) {
        Instant closeAt = row.serviceDate().atTime(row.endTime()).atZone(BUSINESS_ZONE).toInstant();
        return new SlotSnapshotResponse(
                row.slotId().toString(),
                row.outletId().toString(),
                row.itemId().toString(),
                row.outletName(),
                row.itemName(),
                row.serviceDate(),
                row.startTime(),
                row.endTime(),
                row.totalQuota(),
                row.releaseAt(),
                row.checkInStart(),
                row.checkInEnd(),
                closeAt,
                row.status().name(),
                row.configVersion());
    }
}
