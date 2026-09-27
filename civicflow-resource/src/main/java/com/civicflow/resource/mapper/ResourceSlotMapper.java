package com.civicflow.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.resource.dto.response.SlotSnapshotRow;
import com.civicflow.resource.entity.ResourceSlotEntity;
import com.civicflow.resource.enums.SlotStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ResourceSlotMapper extends BaseMapper<ResourceSlotEntity> {
    long countFutureByOutlet(@Param("outletId") long outletId, @Param("today") LocalDate today);

    long countFutureByItem(@Param("itemId") long itemId, @Param("today") LocalDate today);

    long countFutureByWindow(@Param("windowId") long windowId, @Param("today") LocalDate today);

    ResourceSlotEntity selectActiveById(@Param("id") long id);

    SlotSnapshotRow selectSnapshotById(@Param("id") long id);

    ResourceSlotEntity selectByIdForUpdate(@Param("id") long id);

    ResourceSlotEntity selectExact(
            @Param("outletId") long outletId,
            @Param("itemId") long itemId,
            @Param("serviceDate") LocalDate serviceDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime);

    long countOverlapping(
            @Param("outletId") long outletId,
            @Param("itemId") long itemId,
            @Param("serviceDate") LocalDate serviceDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludedId") Long excludedId);

    long countEnabledOffering(@Param("outletId") long outletId, @Param("itemId") long itemId);

    List<ResourceSlotEntity> selectAdminPage(
            @Param("outletId") Long outletId,
            @Param("itemId") Long itemId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("status") SlotStatus status,
            @Param("offset") long offset,
            @Param("size") int size);

    long countAdminPage(
            @Param("outletId") Long outletId,
            @Param("itemId") Long itemId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("status") SlotStatus status);

    List<SlotSnapshotRow> selectPreheatSnapshotCandidates(
            @Param("releaseFrom") Instant releaseFrom,
            @Param("releaseTo") Instant releaseTo,
            @Param("afterId") long afterId,
            @Param("size") int size);

    List<SlotSnapshotRow> selectReconciliationCandidates(
            @Param("at") Instant at,
            @Param("today") LocalDate today,
            @Param("localTime") LocalTime localTime,
            @Param("afterId") long afterId,
            @Param("size") int size);

    int updateDraftDetails(
            @Param("id") long id,
            @Param("outletId") long outletId,
            @Param("itemId") long itemId,
            @Param("serviceDate") LocalDate serviceDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("totalQuota") int totalQuota,
            @Param("releaseAt") Instant releaseAt,
            @Param("checkInStart") Instant checkInStart,
            @Param("checkInEnd") Instant checkInEnd,
            @Param("actorId") long actorId,
            @Param("expectedVersion") int expectedVersion);

    int updateStatus(
            @Param("id") long id,
            @Param("status") SlotStatus status,
            @Param("actorId") long actorId,
            @Param("expectedVersion") int expectedVersion);

    int updateQuota(
            @Param("id") long id,
            @Param("totalQuota") int totalQuota,
            @Param("actorId") long actorId,
            @Param("expectedConfigVersion") long expectedConfigVersion);
}
