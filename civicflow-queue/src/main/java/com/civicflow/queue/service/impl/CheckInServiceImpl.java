package com.civicflow.queue.service.impl;

import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.queue.client.AppointmentCheckInClient;
import com.civicflow.queue.client.ResourceStaffClient;
import com.civicflow.queue.dto.response.QueueTicketResponse;
import com.civicflow.queue.entity.CheckinReconciliationRecordEntity;
import com.civicflow.queue.entity.QueueTicketEntity;
import com.civicflow.queue.enums.QueueActorType;
import com.civicflow.queue.error.QueueErrorCode;
import com.civicflow.queue.mapper.CheckInReconciliationMapper;
import com.civicflow.queue.mapper.QueueTicketMapper;
import com.civicflow.queue.service.CheckInService;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class CheckInServiceImpl implements CheckInService {
    private final AppointmentCheckInClient appointments;
    private final ResourceStaffClient staff;
    private final QueueTicketCreationService creation;
    private final QueueTicketMapper tickets;
    private final CheckInReconciliationMapper reconciliation;

    public CheckInServiceImpl(
            AppointmentCheckInClient appointments,
            ResourceStaffClient staff,
            QueueTicketCreationService creation,
            QueueTicketMapper tickets,
            CheckInReconciliationMapper reconciliation) {
        this.appointments = appointments;
        this.staff = staff;
        this.creation = creation;
        this.tickets = tickets;
        this.reconciliation = reconciliation;
    }

    @Override
    public QueueTicketResponse selfCheckIn(long userId, long outletId, String token) {
        return checkIn(userId, outletId, token, QueueActorType.USER, userId);
    }

    @Override
    public QueueTicketResponse staffCheckIn(long staffUserId, long outletId, String token) {
        var allowed = staff.authorized(staffUserId, outletId);
        if (allowed == null || !Boolean.TRUE.equals(allowed.data())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        // The scanner never chooses a user ID: appointment verifies the signed user claim.
        long userId = tokenUserId(token);
        return checkIn(userId, outletId, token, QueueActorType.STAFF, staffUserId);
    }

    private QueueTicketResponse checkIn(
            long userId, long outletId, String token, QueueActorType actorType, long actorId) {
        AppointmentCheckInClient.ClaimResponse claim =
                appointments
                        .claim(
                                new AppointmentCheckInClient.ClaimRequest(
                                        token, userId, outletId, UUID.randomUUID().toString()))
                        .data();
        if (claim == null
                || !Long.toString(userId).equals(claim.userId())
                || !Long.toString(outletId).equals(claim.outletId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        QueueTicketEntity ticket;
        try {
            ticket = creation.create(claim, actorType, actorId);
        } catch (DuplicateKeyException exception) {
            ticket = tickets.selectByAppointment(Long.parseLong(claim.appointmentId()));
            if (ticket == null) {
                throw new BusinessException(QueueErrorCode.CHECKIN_PENDING, exception);
            }
        } catch (DataAccessException exception) {
            throw new BusinessException(QueueErrorCode.CHECKIN_PENDING, exception);
        }
        CheckinReconciliationRecordEntity record =
                reconciliation.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                                        CheckinReconciliationRecordEntity>()
                                .eq(
                                        CheckinReconciliationRecordEntity::getAppointmentId,
                                        ticket.getAppointmentId()));
        if (record != null) {
            try {
                appointments.link(
                        ticket.getAppointmentId(),
                        new AppointmentCheckInClient.LinkRequest(
                                claim.claimId(), ticket.getId(), UUID.randomUUID().toString()));
                reconciliation.markLinked(record.getId());
            } catch (RuntimeException exception) {
                reconciliation.defer(record.getId(), exception.getClass().getSimpleName());
            }
        }
        return QueueTicketResponse.from(ticket);
    }

    private static long tokenUserId(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || token.length() > 4096) {
                throw new IllegalArgumentException();
            }
            var json =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(java.util.Base64.getUrlDecoder().decode(parts[1]));
            return Long.parseLong(json.path("userId").asText());
        } catch (Exception exception) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
    }
}
