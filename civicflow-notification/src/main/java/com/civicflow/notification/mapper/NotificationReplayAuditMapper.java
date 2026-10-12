package com.civicflow.notification.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface NotificationReplayAuditMapper {
    @Insert(
            "INSERT INTO notification_replay_audit(id,record_id,actor_id,reason,requested_at) VALUES(#{id},#{recordId},#{actorId},#{reason},CURRENT_TIMESTAMP(3))")
    int insert(
            @Param("id") long id,
            @Param("recordId") long recordId,
            @Param("actorId") long actorId,
            @Param("reason") String reason);
}
