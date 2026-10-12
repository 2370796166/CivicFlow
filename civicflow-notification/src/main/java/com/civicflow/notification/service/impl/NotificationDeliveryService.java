package com.civicflow.notification.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.notification.entity.NotificationRecordEntity;
import com.civicflow.notification.entity.NotificationTemplateEntity;
import com.civicflow.notification.event.NotificationEvent;
import com.civicflow.notification.mapper.NotificationPreferenceMapper;
import com.civicflow.notification.mapper.NotificationRecordMapper;
import com.civicflow.notification.mapper.NotificationReplayAuditMapper;
import com.civicflow.notification.mapper.NotificationTemplateMapper;
import com.civicflow.notification.service.DeliveryChannel;
import com.civicflow.notification.service.NotificationStatus;
import com.civicflow.notification.service.NotificationTemplateRenderer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryService {
    private static final Map<String, String> EVENT_TEMPLATES =
            Map.of(
                    "appointment.notification.created",
                    "APPOINTMENT_CREATED",
                    "appointment.notification.reminder",
                    "CONFIRM_REMINDER",
                    "appointment.notification.confirmed",
                    "APPOINTMENT_CONFIRMED",
                    "appointment.notification.cancelled",
                    "APPOINTMENT_CANCELLED",
                    "queue.notification.called",
                    "QUEUE_CALLED",
                    "queue.notification.near",
                    "QUEUE_NEAR",
                    "queue.notification.missed",
                    "QUEUE_MISSED");
    private static final Set<String> OPTIONAL = Set.of("QUEUE_MISSED");
    private final NotificationRecordMapper records;
    private final NotificationTemplateMapper templates;
    private final NotificationPreferenceMapper preferences;
    private final NotificationReplayAuditMapper audits;
    private final NotificationTemplateRenderer renderer;
    private final ObjectMapper jsonMapper;

    public NotificationDeliveryService(
            NotificationRecordMapper records,
            NotificationTemplateMapper templates,
            NotificationPreferenceMapper preferences,
            NotificationReplayAuditMapper audits,
            NotificationTemplateRenderer renderer,
            ObjectMapper jsonMapper) {
        this.records = records;
        this.templates = templates;
        this.preferences = preferences;
        this.audits = audits;
        this.renderer = renderer;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public NotificationRecordEntity prepare(NotificationEvent event, String json) {
        validate(event, json);
        NotificationEvent.Payload payload = event.payload();
        NotificationTemplateEntity template =
                templates.selectOne(
                        Wrappers.<NotificationTemplateEntity>lambdaQuery()
                                .eq(
                                        NotificationTemplateEntity::getTemplateCode,
                                        payload.templateCode())
                                .eq(
                                        NotificationTemplateEntity::getTemplateVersion,
                                        payload.templateVersion()));
        NotificationTemplateRenderer.Rendered rendered =
                renderer.render(template, payload.variables());
        NotificationRecordEntity entry = new NotificationRecordEntity();
        entry.setId(IdWorker.getId());
        entry.setEventId(event.eventId());
        entry.setChannel(DeliveryChannel.IN_APP.name());
        entry.setEventType(event.eventType());
        entry.setTemplateCode(payload.templateCode());
        entry.setTemplateVersion(payload.templateVersion());
        entry.setUserId(Long.parseLong(payload.userId()));
        entry.setAggregateId(payload.aggregateId());
        entry.setRequiredNotice(payload.required());
        entry.setPayloadHash(hash(event));
        entry.setEventJson(json);
        entry.setTitle(rendered.title());
        entry.setBody(rendered.body());
        var preference = preferences.find(entry.getUserId(), DeliveryChannel.IN_APP.name());
        boolean optedOut =
                !payload.required() && preference != null && !preference.getOptionalEnabled();
        entry.setStatus(
                optedOut ? NotificationStatus.SKIPPED.name() : NotificationStatus.PENDING.name());
        records.insertIgnore(entry);
        NotificationRecordEntity persisted =
                records.selectOne(
                        Wrappers.<NotificationRecordEntity>lambdaQuery()
                                .eq(NotificationRecordEntity::getEventId, event.eventId())
                                .eq(
                                        NotificationRecordEntity::getChannel,
                                        DeliveryChannel.IN_APP.name()));
        if (persisted == null || !persisted.getPayloadHash().equals(entry.getPayloadHash())) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return persisted;
    }

    @Transactional
    public boolean claim(long id) {
        return records.claim(id) == 1;
    }

    @Transactional
    public void sent(long id) {
        if (records.markSent(id) != 1) throw new IllegalStateException("Notification claim lost");
    }

    @Transactional
    public void failed(long id, boolean dead) {
        if (records.markFailure(
                        id,
                        dead ? NotificationStatus.DEAD.name() : NotificationStatus.FAILED.name(),
                        "PROVIDER_FAILURE")
                != 1) throw new IllegalStateException("Notification claim lost");
    }

    @Transactional
    public void replay(long actorId, long id, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 256) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        if (records.requestReplay(id) != 1) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        audits.insert(IdWorker.getId(), id, actorId, reason.trim());
    }

    @Transactional
    public void read(long userId, long id) {
        NotificationRecordEntity record = records.selectById(id);
        if (record == null || record.getUserId() != userId)
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        records.markRead(id, userId);
    }

    @Transactional
    public void preference(long userId, boolean enabled) {
        preferences.save(userId, DeliveryChannel.IN_APP.name(), enabled);
    }

    @Transactional
    public int recoverStale() {
        return records.recoverStale();
    }

    private static void validate(NotificationEvent event, String json) {
        if (event == null
                || event.payload() == null
                || event.schemaVersion() != 1
                || event.eventVersion() != 1
                || event.occurredAt() == null
                || event.eventId() == null
                || event.eventType() == null
                || event.producer() == null
                || event.traceId() == null
                || event.traceId().isBlank()
                || json == null
                || json.length() > 4096
                || !EVENT_TEMPLATES
                        .getOrDefault(event.eventType(), "")
                        .equals(event.payload().templateCode())
                || event.payload().templateVersion() != 1
                || event.payload().userId() == null
                || event.payload().aggregateId() == null
                || !(event.producer().equals("civicflow-appointment")
                        || event.producer().equals("civicflow-queue"))
                || !event.eventType()
                        .startsWith(
                                event.producer().equals("civicflow-appointment")
                                        ? "appointment."
                                        : "queue.")
                || !event.payload().userId().matches("[1-9][0-9]{0,18}")
                || !event.payload().aggregateId().matches("[1-9][0-9]{0,18}")
                || event.payload().required() == OPTIONAL.contains(event.payload().templateCode())
                || event.payload().variables() == null) {
            throw new IllegalArgumentException("Invalid notification event");
        }
        UUID.fromString(event.eventId());
    }

    private String hash(NotificationEvent event) {
        try {
            Map<String, Object> canonical = new TreeMap<>();
            canonical.put("schemaVersion", event.schemaVersion());
            canonical.put("eventId", event.eventId());
            canonical.put("eventType", event.eventType());
            canonical.put("eventVersion", event.eventVersion());
            canonical.put("occurredAt", event.occurredAt().toString());
            canonical.put("producer", event.producer());
            canonical.put("traceId", event.traceId());
            canonical.put("correlationId", event.correlationId());
            canonical.put("causationId", event.causationId());
            Map<String, Object> payload = new TreeMap<>();
            payload.put("userId", event.payload().userId());
            payload.put("aggregateId", event.payload().aggregateId());
            payload.put("templateCode", event.payload().templateCode());
            payload.put("templateVersion", event.payload().templateVersion());
            payload.put("variables", new TreeMap<>(event.payload().variables()));
            payload.put("required", event.payload().required());
            canonical.put("payload", payload);
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(jsonMapper.writeValueAsBytes(canonical)));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
