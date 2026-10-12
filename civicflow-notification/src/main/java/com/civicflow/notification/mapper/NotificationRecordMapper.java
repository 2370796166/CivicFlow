package com.civicflow.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.notification.entity.NotificationRecordEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface NotificationRecordMapper extends BaseMapper<NotificationRecordEntity> {
    @Insert(
            "INSERT IGNORE INTO notification_record(id,event_id,channel,event_type,template_code,template_version,user_id,aggregate_id,required_notice,payload_hash,event_json,title,body,status,attempts,created_at,updated_at) VALUES(#{id},#{eventId},#{channel},#{eventType},#{templateCode},#{templateVersion},#{userId},#{aggregateId},#{requiredNotice},#{payloadHash},#{eventJson},#{title},#{body},#{status},0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))")
    int insertIgnore(NotificationRecordEntity record);

    @Update(
            "UPDATE notification_record SET status='PROCESSING', attempts=attempts+1,updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status IN ('PENDING','FAILED')")
    int claim(@Param("id") long id);

    @Update(
            "UPDATE notification_record SET status='SENT',sent_at=CURRENT_TIMESTAMP(3),updated_at=CURRENT_TIMESTAMP(3),last_error_code=NULL WHERE id=#{id} AND status='PROCESSING'")
    int markSent(@Param("id") long id);

    @Update(
            "UPDATE notification_record SET status=#{status},last_error_code=#{code},updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status='PROCESSING'")
    int markFailure(
            @Param("id") long id, @Param("status") String status, @Param("code") String code);

    @Update(
            "UPDATE notification_record SET read_at=CURRENT_TIMESTAMP(3),updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND user_id=#{userId} AND status='SENT' AND read_at IS NULL")
    int markRead(@Param("id") long id, @Param("userId") long userId);

    @Update(
            "UPDATE notification_record SET status='PENDING',attempts=0,last_error_code=NULL,updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status='DEAD'")
    int requestReplay(@Param("id") long id);

    @Update(
            "UPDATE notification_record SET status=CASE WHEN attempts>=3 THEN 'DEAD' ELSE 'FAILED' END,last_error_code='LEASE_EXPIRED',updated_at=CURRENT_TIMESTAMP(3) WHERE status='PROCESSING' AND updated_at<DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 2 MINUTE)")
    int recoverStale();
}
