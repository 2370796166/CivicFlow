package com.civicflow.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.api.PageResponse;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.notification.dto.NotificationFailureView;
import com.civicflow.notification.dto.NotificationView;
import com.civicflow.notification.entity.NotificationPreferenceEntity;
import com.civicflow.notification.entity.NotificationRecordEntity;
import com.civicflow.notification.event.NotificationEvent;
import com.civicflow.notification.mapper.NotificationPreferenceMapper;
import com.civicflow.notification.mapper.NotificationRecordMapper;
import com.civicflow.notification.service.DeliveryChannel;
import com.civicflow.notification.service.NotificationProvider;
import com.civicflow.notification.service.NotificationService;
import com.civicflow.notification.service.NotificationStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationService {
    private final NotificationDeliveryService delivery;
    private final NotificationRecordMapper records;
    private final NotificationPreferenceMapper preferences;
    private final NotificationProvider inApp;
    private final ObjectMapper mapper;

    public NotificationServiceImpl(
            NotificationDeliveryService delivery,
            NotificationRecordMapper records,
            NotificationPreferenceMapper preferences,
            List<NotificationProvider> providers,
            ObjectMapper mapper) {
        this.delivery = delivery;
        this.records = records;
        this.preferences = preferences;
        this.inApp =
                providers.stream()
                        .filter(p -> p.channel() == DeliveryChannel.IN_APP)
                        .findFirst()
                        .orElseThrow();
        this.mapper = mapper;
    }

    @Override
    public void consume(NotificationEvent event, String json) {
        NotificationRecordEntity record = delivery.prepare(event, json);
        if (NotificationStatus.SENT.name().equals(record.getStatus())
                || NotificationStatus.SKIPPED.name().equals(record.getStatus())) return;
        if (NotificationStatus.DEAD.name().equals(record.getStatus())) {
            throw new IllegalStateException("Notification requires audited replay");
        }
        if (!delivery.claim(record.getId())) return;
        try {
            inApp.send(record);
            delivery.sent(record.getId());
        } catch (RuntimeException exception) {
            delivery.failed(record.getId(), record.getAttempts() + 1 >= 3);
            throw exception;
        }
    }

    @Override
    public PageResponse<NotificationView> own(long userId, int page, int size) {
        return page(
                page,
                size,
                Wrappers.<NotificationRecordEntity>lambdaQuery()
                        .eq(NotificationRecordEntity::getUserId, userId)
                        .eq(NotificationRecordEntity::getChannel, DeliveryChannel.IN_APP.name())
                        .ne(NotificationRecordEntity::getStatus, NotificationStatus.SKIPPED.name())
                        .orderByDesc(NotificationRecordEntity::getCreatedAt)
                        .orderByDesc(NotificationRecordEntity::getId));
    }

    @Override
    public PageResponse<NotificationFailureView> failures(int page, int size) {
        if (page < 1 || size < 1 || size > 100)
            throw new BusinessException(CommonErrorCode.VALIDATION);
        IPage<NotificationRecordEntity> result =
                records.selectPage(
                        new Page<>(page, size),
                        Wrappers.<NotificationRecordEntity>lambdaQuery()
                                .in(
                                        NotificationRecordEntity::getStatus,
                                        NotificationStatus.FAILED.name(),
                                        NotificationStatus.DEAD.name())
                                .orderByDesc(NotificationRecordEntity::getUpdatedAt));
        return PageResponse.of(
                result.getRecords().stream().map(NotificationFailureView::from).toList(),
                page,
                size,
                result.getTotal());
    }

    private PageResponse<NotificationView> page(
            int page, int size, LambdaQueryWrapper<NotificationRecordEntity> query) {
        if (page < 1 || size < 1 || size > 100)
            throw new BusinessException(CommonErrorCode.VALIDATION);
        IPage<NotificationRecordEntity> result = records.selectPage(new Page<>(page, size), query);
        return PageResponse.of(
                result.getRecords().stream().map(NotificationView::from).toList(),
                page,
                size,
                result.getTotal());
    }

    @Override
    public void read(long userId, long recordId) {
        delivery.read(userId, recordId);
    }

    @Override
    public boolean preference(long userId) {
        NotificationPreferenceEntity preference =
                preferences.find(userId, DeliveryChannel.IN_APP.name());
        return preference == null || preference.getOptionalEnabled();
    }

    @Override
    public void preference(long userId, boolean enabled) {
        delivery.preference(userId, enabled);
    }

    @Override
    public void replay(long adminId, long recordId, String reason) {
        NotificationRecordEntity record = records.selectById(recordId);
        if (record == null || !NotificationStatus.DEAD.name().equals(record.getStatus())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        NotificationEvent event;
        try {
            event = mapper.readValue(record.getEventJson(), NotificationEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored notification cannot be decoded", exception);
        }
        delivery.replay(adminId, recordId, reason);
        consume(event, record.getEventJson());
    }
}
