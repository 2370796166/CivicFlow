package com.civicflow.queue.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface QueueStateSyncMapper {
    record Pending(Long id, Long ticketId, Long appointmentId, String targetStatus) {}

    @Insert(
            "INSERT INTO queue_state_sync(id,ticket_id,appointment_id,target_status,status,next_attempt_at) VALUES(#{id},#{ticket},#{appointment},#{target},'PENDING',UTC_TIMESTAMP(3))")
    int enqueue(
            @Param("id") long id,
            @Param("ticket") long ticket,
            @Param("appointment") long appointment,
            @Param("target") String target);

    @Select(
            "SELECT q.id,q.ticket_id AS ticketId,q.appointment_id AS appointmentId,q.target_status AS targetStatus FROM queue_state_sync q WHERE q.status='PENDING' AND q.next_attempt_at<=UTC_TIMESTAMP(3) AND (q.target_status!='COMPLETED' OR NOT EXISTS (SELECT 1 FROM queue_state_sync serving WHERE serving.ticket_id=q.ticket_id AND serving.target_status='SERVING' AND serving.status='PENDING')) ORDER BY q.id LIMIT #{limit}")
    List<Pending> due(@Param("limit") int limit);

    @Update(
            "UPDATE queue_state_sync SET status='DONE',updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND status='PENDING'")
    int done(@Param("id") long id);

    @Update(
            "UPDATE queue_state_sync SET attempts=attempts+1,next_attempt_at=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 10 SECOND),last_error_code=#{error},updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND status='PENDING'")
    int defer(@Param("id") long id, @Param("error") String error);
}
