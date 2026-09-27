package com.civicflow.queue.service.impl;

import com.civicflow.queue.client.AppointmentCheckInClient.ClaimResponse;
import com.civicflow.queue.entity.CheckinReconciliationRecordEntity;
import com.civicflow.queue.entity.QueueOperationLogEntity;
import com.civicflow.queue.entity.QueueTicketEntity;
import com.civicflow.queue.enums.CheckinReconciliationStatus;
import com.civicflow.queue.enums.QueueActorType;
import com.civicflow.queue.enums.QueueAppointmentStatus;
import com.civicflow.queue.enums.QueueOperation;
import com.civicflow.queue.enums.QueueTicketStatus;
import com.civicflow.queue.mapper.CheckInReconciliationMapper;
import com.civicflow.queue.mapper.QueueOperationLogMapper;
import com.civicflow.queue.mapper.QueueTicketMapper;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueueTicketCreationService {
    private final QueueTicketMapper tickets;
    private final CheckInReconciliationMapper reconciliation;
    private final QueueOperationLogMapper logs;

    public QueueTicketCreationService(
            QueueTicketMapper tickets,
            CheckInReconciliationMapper reconciliation,
            QueueOperationLogMapper logs) {
        this.tickets = tickets;
        this.reconciliation = reconciliation;
        this.logs = logs;
    }

    @Transactional
    public QueueTicketEntity create(ClaimResponse claim) {
        return create(claim, QueueActorType.SERVICE, Long.parseLong(claim.userId()));
    }

    @Transactional
    public QueueTicketEntity create(ClaimResponse claim, QueueActorType actorType, long actorId) {
        if (claim == null
                || !"CHECKED_IN".equals(claim.status())
                || claim.claimId() == null
                || claim.serviceDate() == null
                || claim.checkedInAt() == null) {
            throw new IllegalArgumentException("Invalid confirmed check-in claim");
        }
        long appointmentId = Long.parseLong(claim.appointmentId());
        QueueTicketEntity existing = tickets.selectByAppointment(appointmentId);
        if (existing != null) {
            return existing;
        }
        long outletId = Long.parseLong(claim.outletId());
        tickets.createCounter(outletId, claim.serviceDate());
        if (tickets.incrementCounter(outletId, claim.serviceDate()) != 1) {
            throw new IllegalStateException("Ticket counter is unavailable");
        }
        int number = tickets.currentCounter(outletId, claim.serviceDate());
        QueueTicketEntity ticket = new QueueTicketEntity();
        ticket.setAppointmentId(appointmentId);
        ticket.setUserId(Long.parseLong(claim.userId()));
        ticket.setOutletId(outletId);
        ticket.setItemId(Long.parseLong(claim.itemId()));
        ticket.setServiceDate(claim.serviceDate());
        ticket.setTicketNo("A" + String.format("%03d", number));
        ticket.setPriority(0);
        ticket.setStatus(QueueTicketStatus.WAITING);
        ticket.setCheckedInAt(claim.checkedInAt());
        ticket.setCallCount(0);
        ticket.setVersion(0);
        tickets.insert(ticket);
        QueueOperationLogEntity audit = new QueueOperationLogEntity();
        audit.setTicketId(ticket.getId());
        audit.setAppointmentId(appointmentId);
        audit.setActorType(actorType);
        audit.setActorId(actorId);
        audit.setOperation(QueueOperation.CHECK_IN);
        audit.setToStatus(QueueTicketStatus.WAITING);
        audit.setRequestId(claim.claimId());
        audit.setOccurredAt(claim.checkedInAt());
        audit.setDetailJson("{\"source\":\"APPOINTMENT_CLAIM\"}");
        logs.insert(audit);
        CheckinReconciliationRecordEntity record = new CheckinReconciliationRecordEntity();
        record.setAppointmentId(appointmentId);
        record.setTicketId(ticket.getId());
        record.setClaimId(claim.claimId());
        record.setAppointmentStatusSnapshot(QueueAppointmentStatus.CHECKED_IN);
        record.setStatus(CheckinReconciliationStatus.TICKET_CREATED);
        record.setAttempts(0);
        record.setNextAttemptAt(Instant.now());
        reconciliation.insert(record);
        return ticket;
    }
}
