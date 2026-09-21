package com.civicflow.resource.service.impl;

import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.resource.dto.response.SlotCandidatePageResponse;
import com.civicflow.resource.dto.response.SlotSnapshotResponse;
import com.civicflow.resource.entity.ResourceSlotEntity;
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
        ResourceSlotEntity entity = mapper.selectActiveById(slotId);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return toResponse(entity);
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
        List<ResourceSlotEntity> rows =
                mapper.selectPreheatCandidates(releaseFrom, releaseTo, afterId, size + 1);
        boolean hasMore = rows.size() > size;
        List<ResourceSlotEntity> page = hasMore ? rows.subList(0, size) : rows;
        String nextCursor =
                page.isEmpty()
                        ? Long.toString(afterId)
                        : page.get(page.size() - 1).getId().toString();
        return new SlotCandidatePageResponse(
                page.stream().map(ResourceSlotSnapshotServiceImpl::toResponse).toList(),
                nextCursor,
                hasMore);
    }

    private static SlotSnapshotResponse toResponse(ResourceSlotEntity entity) {
        Instant closeAt =
                entity.getServiceDate()
                        .atTime(entity.getEndTime())
                        .atZone(BUSINESS_ZONE)
                        .toInstant();
        return new SlotSnapshotResponse(
                entity.getId().toString(),
                entity.getOutletId().toString(),
                entity.getItemId().toString(),
                entity.getServiceDate(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getTotalQuota(),
                entity.getReleaseAt(),
                closeAt,
                entity.getStatus().name(),
                entity.getConfigVersion());
    }
}
