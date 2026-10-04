package com.civicflow.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.appointment.entity.AppointmentCommandIdempotencyEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AppointmentCommandIdempotencyMapper
        extends BaseMapper<AppointmentCommandIdempotencyEntity> {
    @Insert(
            "INSERT IGNORE INTO appointment_command_idempotency"
                    + " (id,actor_user_id,appointment_id,operation,idempotency_key_hash,payload_hash)"
                    + " VALUES (#{id},#{actorUserId},#{appointmentId},#{operation},#{keyHash},#{payloadHash})")
    int insertIgnore(
            @Param("id") long id,
            @Param("actorUserId") long actorUserId,
            @Param("appointmentId") long appointmentId,
            @Param("operation") String operation,
            @Param("keyHash") byte[] keyHash,
            @Param("payloadHash") byte[] payloadHash);

    @Select(
            "SELECT * FROM appointment_command_idempotency WHERE actor_user_id=#{actorUserId}"
                    + " AND operation=#{operation} AND idempotency_key_hash=#{keyHash} FOR UPDATE")
    AppointmentCommandIdempotencyEntity selectLocked(
            @Param("actorUserId") long actorUserId,
            @Param("operation") String operation,
            @Param("keyHash") byte[] keyHash);
}
