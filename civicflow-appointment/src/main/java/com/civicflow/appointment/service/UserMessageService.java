package com.civicflow.appointment.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.civicflow.common.api.*;
import com.civicflow.common.exception.BusinessException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserMessageService {
    private final JdbcTemplate jdbc;

    public UserMessageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Message(
            String id,
            String appointmentId,
            String title,
            String content,
            Instant readAt,
            Instant createdAt) {}

    public PageResponse<Message> list(long user, int page, int size) {
        long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM user_message WHERE user_id=?", Long.class, user);
        var rows =
                jdbc.query(
                        "SELECT id,appointment_id,title,content,read_at,created_at FROM user_message WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                        (r, n) ->
                                new Message(
                                        r.getString("id"),
                                        r.getString("appointment_id"),
                                        r.getString("title"),
                                        r.getString("content"),
                                        r.getTimestamp("read_at") == null
                                                ? null
                                                : r.getTimestamp("read_at").toInstant(),
                                        r.getTimestamp("created_at").toInstant()),
                        user,
                        size,
                        (long) (page - 1) * size);
        return PageResponse.of(rows, page, size, total);
    }

    public long unread(long user) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_message WHERE user_id=? AND read_at IS NULL",
                Long.class,
                user);
    }

    public void read(long user, long id) {
        if (jdbc.update(
                                "UPDATE user_message SET read_at=CURRENT_TIMESTAMP(3) WHERE id=? AND user_id=? AND read_at IS NULL",
                                id,
                                user)
                        == 0
                && jdbc.queryForObject(
                                "SELECT COUNT(*) FROM user_message WHERE id=? AND user_id=?",
                                Long.class,
                                id,
                                user)
                        == 0) throw new BusinessException(CommonErrorCode.NOT_FOUND);
    }

    public void send(long user, long appointment, String source, String title, String content) {
        jdbc.update(
                "INSERT IGNORE INTO user_message(id,user_id,appointment_id,source_key,title,content) VALUES(?,?,?,?,?,?)",
                IdWorker.getId(),
                user,
                appointment,
                source,
                title,
                content);
    }
}
