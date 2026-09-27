package com.civicflow.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.appointment.entity.AppointmentOrderEntity;
import com.civicflow.appointment.enums.AppointmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AppointmentOrderMapper extends BaseMapper<AppointmentOrderEntity> {
    AppointmentOrderEntity selectByReservationId(@Param("reservationId") String reservationId);

    AppointmentOrderEntity selectOwnedById(@Param("id") long id, @Param("userId") long userId);

    List<AppointmentOrderEntity> selectOwnedPage(
            @Param("userId") long userId,
            @Param("serviceDate") LocalDate serviceDate,
            @Param("status") AppointmentStatus status,
            @Param("offset") long offset,
            @Param("size") int size);

    long countOwnedPage(
            @Param("userId") long userId,
            @Param("serviceDate") LocalDate serviceDate,
            @Param("status") AppointmentStatus status);

    int confirm(@Param("id") long id, @Param("userId") long userId, @Param("version") int version);

    int cancel(
            @Param("id") long id,
            @Param("userId") long userId,
            @Param("version") int version,
            @Param("reason") String reason,
            @Param("cancelCutoff") Instant cancelCutoff);

    int expire(@Param("id") long id);

    List<Long> selectExpiredIds(@Param("limit") int limit);

    int setQr(
            @Param("id") long id,
            @Param("userId") long userId,
            @Param("hash") byte[] hash,
            @Param("expiresAt") Instant expiresAt,
            @Param("keyId") String keyId);

    int claimCheckIn(
            @Param("id") long id,
            @Param("userId") long userId,
            @Param("outletId") long outletId,
            @Param("hash") byte[] hash,
            @Param("claimId") String claimId);

    int linkTicket(
            @Param("id") long id,
            @Param("claimId") String claimId,
            @Param("ticketId") long ticketId);

    int applyQueueState(
            @Param("id") long id,
            @Param("ticketId") long ticketId,
            @Param("from") String from,
            @Param("to") String to);
}
