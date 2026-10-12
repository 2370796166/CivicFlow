package com.civicflow.notification.mapper;

import com.civicflow.notification.entity.NotificationPreferenceEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface NotificationPreferenceMapper {
    @Select("SELECT * FROM notification_preference WHERE user_id=#{userId} AND channel=#{channel}")
    NotificationPreferenceEntity find(
            @Param("userId") long userId, @Param("channel") String channel);

    @Update(
            "INSERT INTO notification_preference(user_id,channel,optional_enabled,updated_at) VALUES(#{userId},#{channel},#{enabled},CURRENT_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE optional_enabled=VALUES(optional_enabled),updated_at=CURRENT_TIMESTAMP(3)")
    int save(
            @Param("userId") long userId,
            @Param("channel") String channel,
            @Param("enabled") boolean enabled);
}
