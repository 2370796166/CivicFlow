package com.civicflow.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.appointment.entity.AppointmentReservationRequestEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AppointmentReservationRequestMapper
        extends BaseMapper<AppointmentReservationRequestEntity> {
    int saveReservedEvent(@Param("request") AppointmentReservationRequestEntity request);

    int recordPublication(@Param("request") AppointmentReservationRequestEntity request);

    List<AppointmentReservationRequestEntity> selectRecoverable(
            @Param("now") Instant now, @Param("size") int size);

    int claimRecovery(
            @Param("id") long id,
            @Param("owner") String owner,
            @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil);
}
