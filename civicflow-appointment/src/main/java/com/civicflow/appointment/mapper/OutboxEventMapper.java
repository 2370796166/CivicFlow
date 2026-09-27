package com.civicflow.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.appointment.entity.OutboxEventEntity;
import com.civicflow.appointment.event.AppointmentMessaging;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface OutboxEventMapper extends BaseMapper<OutboxEventEntity> {
    @Select(
            "SELECT * FROM outbox_event WHERE event_type='"
                    + AppointmentMessaging.TIMEOUT_EVENT_TYPE
                    + "'"
                    + " AND ((status IN ('PENDING','FAILED') AND next_attempt_at<=CURRENT_TIMESTAMP(3))"
                    + " OR (status='PUBLISHING' AND updated_at<DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 30 SECOND)))"
                    + " ORDER BY id LIMIT #{limit}")
    List<OutboxEventEntity> selectDue(@Param("limit") int limit);

    @Update(
            "UPDATE outbox_event SET status='PUBLISHING',attempts=attempts+1,"
                    + "updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{id}"
                    + " AND ((status IN ('PENDING','FAILED') AND next_attempt_at<=CURRENT_TIMESTAMP(3))"
                    + " OR (status='PUBLISHING' AND updated_at<DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 30 SECOND)))")
    int claim(@Param("id") long id);

    @Select(
            "SELECT * FROM outbox_event WHERE event_type='appointment.check-in.claimed'"
                    + " AND ((status IN ('PENDING','FAILED') AND next_attempt_at<=CURRENT_TIMESTAMP(3))"
                    + " OR (status='PUBLISHING' AND updated_at<DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 30 SECOND)))"
                    + " ORDER BY id LIMIT #{limit}")
    List<OutboxEventEntity> selectDueCheckIn(@Param("limit") int limit);
}
