package com.civicflow.queue.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface QueueStateSyncMapper {
    record Pending(Long id, Long ticketId, Long appointmentId, String targetStatus, int attempts) {}

    @Insert(
            "INSERT INTO queue_state_sync(id,ticket_id,appointment_id,target_status,status,next_attempt_at) VALUES(#{id},#{ticket},#{appointment},#{target},'PENDING',UTC_TIMESTAMP(3))")
    int enqueue(
            @Param("id") long id,
            @Param("ticket") long ticket,
            @Param("appointment") long appointment,
            @Param("target") String target);

    @Select(
            "SELECT q.id,q.ticket_id AS ticketId,q.appointment_id AS appointmentId,q.target_status AS targetStatus,q.attempts FROM queue_state_sync q WHERE ((q.status='PENDING' AND q.next_attempt_at<=UTC_TIMESTAMP(3)) OR (q.status='PROCESSING' AND q.lease_until<=UTC_TIMESTAMP(3))) AND (q.target_status!='COMPLETED' OR NOT EXISTS(SELECT 1 FROM queue_state_sync serving WHERE serving.ticket_id=q.ticket_id AND serving.target_status='SERVING' AND serving.status!='DONE')) ORDER BY q.next_attempt_at,q.id LIMIT #{limit}")
    List<Pending> due(@Param("limit") int limit);

    @Update(
            "UPDATE queue_state_sync SET status='PROCESSING',lease_owner=#{owner},lease_until=#{until},attempts=attempts+1,updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND ((status='PENDING' AND next_attempt_at<=UTC_TIMESTAMP(3)) OR (status='PROCESSING' AND lease_until<=UTC_TIMESTAMP(3)))")
    int claim(@Param("id") long id, @Param("owner") String owner, @Param("until") Instant until);

    @Update(
            "UPDATE queue_state_sync SET status='DONE',lease_owner=NULL,lease_until=NULL,updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND status='PROCESSING' AND lease_owner=#{owner}")
    int done(@Param("id") long id, @Param("owner") String owner);

    @Update(
            "UPDATE queue_state_sync SET status=#{status},next_attempt_at=#{next},last_error_code=#{error},lease_owner=NULL,lease_until=NULL,updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND status='PROCESSING' AND lease_owner=#{owner}")
    int defer(
            @Param("id") long id,
            @Param("owner") String owner,
            @Param("status") String status,
            @Param("next") Instant next,
            @Param("error") String error);
}
