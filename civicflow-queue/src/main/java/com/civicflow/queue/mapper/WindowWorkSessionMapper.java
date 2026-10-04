package com.civicflow.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.queue.entity.WindowWorkSessionEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface WindowWorkSessionMapper extends BaseMapper<WindowWorkSessionEntity> {
    @Insert(
            "INSERT INTO active_window_session_guard(window_id,session_id,staff_user_id) VALUES(#{window},#{session},#{staff})")
    int guard(
            @Param("window") long window,
            @Param("session") long session,
            @Param("staff") long staff);

    @Select(
            "SELECT COUNT(*) FROM queue_ticket WHERE work_session_id=#{session} AND status IN ('CALLED','SERVING')")
    long activeTicketCount(@Param("session") long session);

    @Select(
            "SELECT s.* FROM window_work_session s JOIN active_window_session_guard g ON g.session_id=s.id WHERE s.id=#{id} AND s.staff_user_id=#{staff} AND s.status='ACTIVE' FOR UPDATE")
    WindowWorkSessionEntity active(@Param("id") long id, @Param("staff") long staff);

    @Select(
            "SELECT s.* FROM window_work_session s JOIN active_window_session_guard g ON g.session_id=s.id WHERE s.window_id=#{window} AND s.staff_user_id=#{staff} AND s.status='ACTIVE'")
    WindowWorkSessionEntity current(@Param("window") long window, @Param("staff") long staff);

    @Update(
            "UPDATE window_work_session SET status='ENDED',ended_at=UTC_TIMESTAMP(3),version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND staff_user_id=#{staff} AND status='ACTIVE' AND version=#{version}")
    int end(@Param("id") long id, @Param("staff") long staff, @Param("version") int version);

    @Update(
            "DELETE FROM active_window_session_guard WHERE session_id=#{id} AND staff_user_id=#{staff}")
    int release(@Param("id") long id, @Param("staff") long staff);
}
