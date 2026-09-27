package com.civicflow.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.queue.entity.QueueTicketEntity;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface QueueTicketMapper extends BaseMapper<QueueTicketEntity> {
    QueueTicketEntity selectByAppointment(@Param("appointmentId") long appointmentId);

    int createCounter(@Param("outletId") long outletId, @Param("date") LocalDate date);

    int incrementCounter(@Param("outletId") long outletId, @Param("date") LocalDate date);

    Integer currentCounter(@Param("outletId") long outletId, @Param("date") LocalDate date);

    QueueTicketEntity lockNext(
            @Param("outlet") long outlet,
            @Param("date") LocalDate date,
            @Param("items") List<Long> items);

    int transition(
            @Param("id") long id,
            @Param("from") String from,
            @Param("to") String to,
            @Param("version") int version,
            @Param("window") long window,
            @Param("session") long session,
            @Param("result") String result);

    long ahead(
            @Param("outlet") long outlet,
            @Param("date") LocalDate date,
            @Param("item") long item,
            @Param("priority") int priority,
            @Param("checkedInAt") java.time.Instant checkedInAt,
            @Param("id") long id);

    QueueTicketEntity currentCall(@Param("outlet") long outlet, @Param("date") LocalDate date);

    long countByStatus(
            @Param("outlet") long outlet,
            @Param("date") LocalDate date,
            @Param("status") String status);

    List<QueueTicketEntity> ownCurrent(
            @Param("user") long user,
            @Param("date") LocalDate date,
            @Param("appointment") Long appointment);

    QueueTicketEntity currentBySession(@Param("session") long session);

    record OverviewRow(Long itemId, Long windowId, String status, Long count) {}

    List<OverviewRow> overview(@Param("outlet") long outlet, @Param("date") LocalDate date);
}
