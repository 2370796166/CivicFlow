package com.civicflow.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.queue.entity.OutboxEventEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface QueueNotificationOutboxMapper extends BaseMapper<OutboxEventEntity> {
    @Insert(
            "INSERT IGNORE INTO outbox_event(id,event_id,aggregate_type,aggregate_id,event_type,event_version,routing_key,payload_json,status,attempts,next_attempt_at) VALUES(#{id},#{eventId},#{aggregateType},#{aggregateId},#{eventType},#{eventVersion},#{routingKey},#{payloadJson},#{status},#{attempts},#{nextAttemptAt})")
    int insertIgnore(OutboxEventEntity event);

    @Select(
            "SELECT * FROM outbox_event WHERE event_type LIKE 'queue.notification.%' AND ((status IN ('PENDING','FAILED') AND next_attempt_at<=CURRENT_TIMESTAMP(3)) OR (status='PUBLISHING' AND updated_at<DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 30 SECOND))) ORDER BY id LIMIT #{limit}")
    List<OutboxEventEntity> due(@Param("limit") int limit);

    int claimNotification(@Param("id") long id, @Param("attempts") int attempts);

    int finishNotification(
            @Param("id") long id,
            @Param("attempts") int attempts,
            @Param("status") String status,
            @Param("publishedAt") java.time.Instant publishedAt,
            @Param("error") String error);
}
