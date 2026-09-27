package com.civicflow.queue.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface QueueCommandMapper {
    record Saved(String payloadHash, Long resultId) {}

    @Insert(
            "INSERT INTO queue_command_idempotency(id,staff_user_id,operation,idempotency_key,payload_hash) VALUES(#{id},#{staff},#{operation},#{key},#{hash})")
    int begin(
            @Param("id") long id,
            @Param("staff") long staff,
            @Param("operation") String operation,
            @Param("key") String key,
            @Param("hash") String hash);

    @Update("UPDATE queue_command_idempotency SET result_id=#{resultId} WHERE id=#{id}")
    int finish(@Param("id") long id, @Param("resultId") Long resultId);

    @Select(
            "SELECT payload_hash AS payloadHash,result_id AS resultId FROM queue_command_idempotency WHERE staff_user_id=#{staff} AND operation=#{operation} AND idempotency_key=#{key}")
    Saved find(
            @Param("staff") long staff,
            @Param("operation") String operation,
            @Param("key") String key);
}
