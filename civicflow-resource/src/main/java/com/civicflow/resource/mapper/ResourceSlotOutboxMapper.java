package com.civicflow.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.resource.entity.ResourceSlotOutboxEntity;

public interface ResourceSlotOutboxMapper extends BaseMapper<ResourceSlotOutboxEntity> {
    @org.apache.ibatis.annotations.Select(
            "SELECT o.* FROM resource_slot_outbox o WHERE o.status IN ('PENDING','FAILED','PUBLISHING') AND o.next_attempt_at<=#{now} AND NOT EXISTS (SELECT 1 FROM resource_slot_outbox prior WHERE prior.slot_id=o.slot_id AND prior.id<o.id AND prior.status!='PUBLISHED') ORDER BY o.id LIMIT #{size}")
    java.util.List<ResourceSlotOutboxEntity> due(
            @org.apache.ibatis.annotations.Param("now") java.time.Instant now,
            @org.apache.ibatis.annotations.Param("size") int size);

    @org.apache.ibatis.annotations.Update(
            "UPDATE resource_slot_outbox SET status='PUBLISHING',attempts=attempts+1,next_attempt_at=#{lease},updated_at=#{now} WHERE id=#{id} AND attempts=#{attempts} AND status IN ('PENDING','FAILED','PUBLISHING') AND next_attempt_at<=#{now}")
    int claim(
            @org.apache.ibatis.annotations.Param("id") long id,
            @org.apache.ibatis.annotations.Param("attempts") int attempts,
            @org.apache.ibatis.annotations.Param("now") java.time.Instant now,
            @org.apache.ibatis.annotations.Param("lease") java.time.Instant lease);

    @org.apache.ibatis.annotations.Update(
            "UPDATE resource_slot_outbox SET status=#{status},published_at=#{published},next_attempt_at=#{next},last_error_code=#{error},updated_at=#{now} WHERE id=#{id} AND status='PUBLISHING' AND attempts=#{attempts}")
    int finish(
            @org.apache.ibatis.annotations.Param("id") long id,
            @org.apache.ibatis.annotations.Param("attempts") int attempts,
            @org.apache.ibatis.annotations.Param("status") String status,
            @org.apache.ibatis.annotations.Param("published") java.time.Instant published,
            @org.apache.ibatis.annotations.Param("next") java.time.Instant next,
            @org.apache.ibatis.annotations.Param("error") String error,
            @org.apache.ibatis.annotations.Param("now") java.time.Instant now);
}
