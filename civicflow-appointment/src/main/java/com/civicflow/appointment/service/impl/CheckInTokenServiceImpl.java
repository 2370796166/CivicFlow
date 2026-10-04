package com.civicflow.appointment.service.impl;

import com.civicflow.appointment.client.ResourceSlotClient;
import com.civicflow.appointment.client.ResourceSlotSnapshot;
import com.civicflow.appointment.config.AppointmentProperties;
import com.civicflow.appointment.dto.response.CheckInClaimResponse;
import com.civicflow.appointment.dto.response.CheckInTokenResponse;
import com.civicflow.appointment.entity.AppointmentOperationLogEntity;
import com.civicflow.appointment.entity.AppointmentOrderEntity;
import com.civicflow.appointment.entity.OutboxEventEntity;
import com.civicflow.appointment.enums.AppointmentActorType;
import com.civicflow.appointment.enums.AppointmentOperation;
import com.civicflow.appointment.enums.AppointmentStatus;
import com.civicflow.appointment.enums.OutboxStatus;
import com.civicflow.appointment.error.AppointmentErrorCode;
import com.civicflow.appointment.event.AppointmentMessaging;
import com.civicflow.appointment.mapper.AppointmentOperationLogMapper;
import com.civicflow.appointment.mapper.AppointmentOrderMapper;
import com.civicflow.appointment.mapper.OutboxEventMapper;
import com.civicflow.appointment.service.CheckInTokenService;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckInTokenServiceImpl implements CheckInTokenService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String PURPOSE = "CHECK_IN";
    private final AppointmentOrderMapper orders;
    private final ResourceSlotClient slots;
    private final AppointmentProperties properties;
    private final Clock clock;
    private final OutboxEventMapper outbox;
    private final AppointmentOperationLogMapper logs;
    private final ObjectMapper json;
    private final byte[] signingKey;
    private final SecureRandom random = new SecureRandom();

    public CheckInTokenServiceImpl(
            AppointmentOrderMapper orders,
            ResourceSlotClient slots,
            AppointmentProperties properties,
            Clock clock,
            OutboxEventMapper outbox,
            ObjectMapper json,
            AppointmentOperationLogMapper logs) {
        this.orders = orders;
        this.slots = slots;
        this.properties = properties;
        this.clock = clock;
        this.outbox = outbox;
        this.json = json;
        this.logs = logs;
        String configured = properties.getCheckIn().getSigningKeyBase64();
        if (configured == null || configured.isBlank()) {
            if (!properties.getCheckIn().isAllowTestKey()) {
                throw new IllegalStateException("Independent check-in signing key is required");
            }
            signingKey = new byte[32];
            random.nextBytes(signingKey);
        } else {
            signingKey = Base64.getDecoder().decode(configured);
        }
        if (signingKey.length < 32) {
            throw new IllegalStateException("Check-in signing key must be at least 256 bits");
        }
    }

    @Override
    @Transactional
    public CheckInTokenResponse issue(long userId, long appointmentId, long outletId) {
        AppointmentOrderEntity order = orders.selectOwnedById(appointmentId, userId);
        if (order == null || order.getOutletId() != outletId) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        if (order.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessException(AppointmentErrorCode.STATE_CONFLICT);
        }
        Instant now = clock.instant();
        ResourceSlotSnapshot slot = checkedSlot(order, outletId, now);
        Instant expires = now.plus(properties.getCheckIn().getTokenTtl());
        if (expires.isAfter(slot.checkInEnd())) {
            expires = slot.checkInEnd();
        }
        byte[] nonceBytes = new byte[24];
        random.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        if (orders.setQr(
                        appointmentId,
                        userId,
                        nonceHash(nonce),
                        expires,
                        properties.getCheckIn().getKeyId())
                != 1) {
            throw new BusinessException(AppointmentErrorCode.STATE_CONFLICT);
        }
        try {
            JWTClaimsSet claims =
                    new JWTClaimsSet.Builder()
                            .claim("appointmentId", Long.toString(appointmentId))
                            .claim("userId", Long.toString(userId))
                            .claim("outletId", Long.toString(outletId))
                            .claim("nonce", nonce)
                            .claim("purpose", PURPOSE)
                            .issueTime(Date.from(now))
                            .expirationTime(Date.from(expires))
                            .build();
            SignedJWT jwt =
                    new SignedJWT(
                            new JWSHeader.Builder(JWSAlgorithm.HS256)
                                    .keyID(properties.getCheckIn().getKeyId())
                                    .build(),
                            claims);
            jwt.sign(new MACSigner(signingKey));
            return new CheckInTokenResponse(jwt.serialize(), expires);
        } catch (JOSEException exception) {
            throw new IllegalStateException("Check-in token signing failed", exception);
        }
    }

    @Override
    @Transactional
    public CheckInClaimResponse claim(
            String token, long userId, long outletId, String claimId, String traceId) {
        if (claimId == null
                || !claimId.matches(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        JWTClaimsSet claims = verify(token);
        long appointmentId = parseId(claims, "appointmentId");
        if (parseId(claims, "userId") != userId || parseId(claims, "outletId") != outletId) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        AppointmentOrderEntity order = orders.selectOwnedById(appointmentId, userId);
        if (order == null || order.getOutletId() != outletId) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        String nonce = (String) claims.getClaim("nonce");
        if (nonce == null || nonce.length() > 128) {
            throw new BusinessException(AppointmentErrorCode.QR_EXPIRED);
        }
        byte[] hash = nonceHash(nonce);
        if (order.getQrNonceHash() == null || !Arrays.equals(order.getQrNonceHash(), hash)) {
            throw new BusinessException(AppointmentErrorCode.QR_EXPIRED);
        }
        if (order.getStatus() == AppointmentStatus.CHECKED_IN
                && order.getCheckinClaimId() != null) {
            return snapshot(order);
        }
        checkedSlot(order, outletId, clock.instant());
        if (orders.claimCheckIn(appointmentId, userId, outletId, hash, claimId) != 1) {
            order = orders.selectOwnedById(appointmentId, userId);
            if (order != null
                    && order.getStatus() == AppointmentStatus.CHECKED_IN
                    && Arrays.equals(order.getQrNonceHash(), hash)) {
                return snapshot(order);
            }
            throw new BusinessException(AppointmentErrorCode.STATE_CONFLICT);
        }
        AppointmentOrderEntity claimed = orders.selectOwnedById(appointmentId, userId);
        AppointmentOperationLogEntity audit = new AppointmentOperationLogEntity();
        audit.setAppointmentId(claimed.getId());
        audit.setReservationId(claimed.getReservationId());
        audit.setActorType(AppointmentActorType.SERVICE);
        audit.setActorId(userId);
        audit.setOperation(AppointmentOperation.CHECK_IN);
        audit.setFromStatus(AppointmentStatus.CONFIRMED);
        audit.setToStatus(AppointmentStatus.CHECKED_IN);
        audit.setRequestId(traceId);
        audit.setOccurredAt(clock.instant());
        audit.setDetailJson("{\"source\":\"QUEUE_CHECK_IN\"}");
        logs.insert(audit);
        writeClaimEvent(claimed, traceId);
        return snapshot(claimed);
    }

    @Override
    @Transactional
    public void link(long appointmentId, String claimId, long ticketId) {
        AppointmentOrderEntity order = orders.selectById(appointmentId);
        if (order == null || !claimId.equals(order.getCheckinClaimId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        if (order.getQueueTicketId() != null && ticketId == order.getQueueTicketId()) {
            return;
        }
        if (order.getQueueTicketId() != null
                || orders.linkTicket(appointmentId, claimId, ticketId) != 1) {
            throw new BusinessException(AppointmentErrorCode.STATE_CONFLICT);
        }
    }

    private ResourceSlotSnapshot checkedSlot(
            AppointmentOrderEntity order, long outletId, Instant now) {
        var response = slots.getSnapshot(order.getSlotId());
        ResourceSlotSnapshot slot = response == null ? null : response.data();
        if (slot == null
                || slot.checkInStart() == null
                || slot.checkInEnd() == null
                || !Long.toString(outletId).equals(slot.outletId())
                || !slot.serviceDate().equals(order.getServiceDate())
                || !LocalDate.ofInstant(now, BUSINESS_ZONE).equals(order.getServiceDate())
                || now.isBefore(slot.checkInStart())
                || !now.isBefore(slot.checkInEnd())) {
            throw new BusinessException(AppointmentErrorCode.CHECKIN_WINDOW);
        }
        return slot;
    }

    private void writeClaimEvent(AppointmentOrderEntity order, String traceId) {
        String eventId = UUID.randomUUID().toString();
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("eventId", eventId);
        payload.put("eventType", AppointmentMessaging.CHECKIN_CLAIMED_EVENT_TYPE);
        payload.put("eventVersion", 1);
        payload.put("occurredAt", clock.instant());
        payload.put("traceId", traceId);
        payload.put("producer", "civicflow-appointment");
        payload.put("idempotencyKey", order.getId().toString());
        payload.put("claimId", order.getCheckinClaimId());
        payload.put("appointmentId", order.getId().toString());
        payload.put("userId", order.getUserId().toString());
        payload.put("outletId", order.getOutletId().toString());
        payload.put("itemId", order.getItemId().toString());
        payload.put("serviceDate", order.getServiceDate());
        payload.put("checkedInAt", order.getCheckedInAt());
        OutboxEventEntity entry = new OutboxEventEntity();
        entry.setEventId(eventId);
        entry.setAggregateType("appointment");
        entry.setAggregateId(order.getId().toString());
        entry.setEventType(AppointmentMessaging.CHECKIN_CLAIMED_EVENT_TYPE);
        entry.setEventVersion(1);
        entry.setRoutingKey(AppointmentMessaging.CHECKIN_CLAIMED_ROUTING_KEY);
        try {
            entry.setPayloadJson(json.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Check-in event serialization failed", exception);
        }
        entry.setStatus(OutboxStatus.PENDING);
        entry.setAttempts(0);
        entry.setNextAttemptAt(clock.instant());
        outbox.insert(entry);
    }

    private JWTClaimsSet verify(String token) {
        try {
            if (token == null || token.length() > 4096) {
                throw new BusinessException(AppointmentErrorCode.QR_EXPIRED);
            }
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !properties.getCheckIn().getKeyId().equals(jwt.getHeader().getKeyID())
                    || !jwt.verify(new MACVerifier(signingKey))) {
                throw new BusinessException(AppointmentErrorCode.QR_EXPIRED);
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Instant now = clock.instant();
            if (!PURPOSE.equals(claims.getClaim("purpose"))
                    || claims.getIssueTime() == null
                    || claims.getExpirationTime() == null
                    || claims.getIssueTime().toInstant().isAfter(now)
                    || !claims.getExpirationTime().toInstant().isAfter(now)
                    || claims.getExpirationTime()
                            .toInstant()
                            .isAfter(
                                    claims.getIssueTime()
                                            .toInstant()
                                            .plus(properties.getCheckIn().getTokenTtl()))) {
                throw new BusinessException(AppointmentErrorCode.QR_EXPIRED);
            }
            return claims;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(AppointmentErrorCode.QR_EXPIRED, exception);
        }
    }

    private byte[] nonceHash(String nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return mac.doFinal(nonce.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Nonce HMAC failed", exception);
        }
    }

    private long parseId(JWTClaimsSet claims, String name) {
        try {
            return Long.parseLong((String) claims.getClaim(name));
        } catch (RuntimeException exception) {
            throw new BusinessException(AppointmentErrorCode.QR_EXPIRED);
        }
    }

    private static CheckInClaimResponse snapshot(AppointmentOrderEntity order) {
        return new CheckInClaimResponse(
                order.getCheckinClaimId(),
                order.getId().toString(),
                order.getUserId().toString(),
                order.getOutletId().toString(),
                order.getItemId().toString(),
                order.getServiceDate(),
                order.getCheckedInAt(),
                order.getStatus().name());
    }
}
