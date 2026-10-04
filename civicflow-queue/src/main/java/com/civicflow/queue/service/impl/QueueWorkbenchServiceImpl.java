package com.civicflow.queue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.exception.BusinessException;
import com.civicflow.queue.client.ResourceStaffClient;
import com.civicflow.queue.dto.response.CurrentWorkSessionResponse;
import com.civicflow.queue.dto.response.QueueOverviewGroupResponse;
import com.civicflow.queue.dto.response.QueueProgressResponse;
import com.civicflow.queue.dto.response.QueueTicketResponse;
import com.civicflow.queue.dto.response.WorkSessionResponse;
import com.civicflow.queue.entity.QueueOperationLogEntity;
import com.civicflow.queue.entity.QueueTicketEntity;
import com.civicflow.queue.entity.WindowWorkSessionEntity;
import com.civicflow.queue.enums.QueueActorType;
import com.civicflow.queue.enums.QueueOperation;
import com.civicflow.queue.enums.QueueTicketStatus;
import com.civicflow.queue.enums.WindowSessionStatus;
import com.civicflow.queue.error.QueueErrorCode;
import com.civicflow.queue.mapper.QueueCommandMapper;
import com.civicflow.queue.mapper.QueueOperationLogMapper;
import com.civicflow.queue.mapper.QueueStateSyncMapper;
import com.civicflow.queue.mapper.QueueTicketMapper;
import com.civicflow.queue.mapper.WindowWorkSessionMapper;
import com.civicflow.queue.service.QueueWorkbenchService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class QueueWorkbenchServiceImpl implements QueueWorkbenchService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final ResourceStaffClient resources;
    private final QueueTicketMapper tickets;
    private final WindowWorkSessionMapper sessions;
    private final QueueOperationLogMapper logs;
    private final QueueCommandMapper commands;
    private final QueueStateSyncMapper sync;
    private final TransactionTemplate tx;

    public QueueWorkbenchServiceImpl(
            ResourceStaffClient resources,
            QueueTicketMapper tickets,
            WindowWorkSessionMapper sessions,
            QueueOperationLogMapper logs,
            QueueCommandMapper commands,
            QueueStateSyncMapper sync,
            PlatformTransactionManager manager) {
        this.resources = resources;
        this.tickets = tickets;
        this.sessions = sessions;
        this.logs = logs;
        this.commands = commands;
        this.sync = sync;
        this.tx = new TransactionTemplate(manager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    private ResourceStaffClient.Scope scope(long staff, long window) {
        var response = resources.scopes(staff);
        if (response == null || response.data() == null)
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        return response.data().stream()
                .filter(s -> Long.toString(window).equals(s.windowId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private static List<Long> items(ResourceStaffClient.Scope scope) {
        return scope.items().stream().map(i -> Long.parseLong(i.id())).distinct().toList();
    }

    @Override
    public WorkSessionResponse start(long staff, long window, String key, String requestId) {
        ResourceStaffClient.Scope scope = scope(staff, window);
        long outlet = Long.parseLong(scope.outletId());
        return command(
                staff,
                "START_SESSION",
                key,
                window + ":" + outlet,
                () -> {
                    WindowWorkSessionEntity session = new WindowWorkSessionEntity();
                    session.setId(IdWorker.getId());
                    session.setWindowId(window);
                    session.setOutletId(outlet);
                    session.setStaffUserId(staff);
                    session.setStatus(WindowSessionStatus.ACTIVE);
                    session.setStartedAt(Instant.now());
                    session.setVersion(0);
                    sessions.insert(session);
                    if (sessions.guard(window, session.getId(), staff) != 1) throw conflict();
                    log(null, staff, QueueOperation.START_SESSION, null, null, requestId);
                    return session.getId();
                },
                id -> WorkSessionResponse.from(sessions.selectById(id)));
    }

    @Override
    public WorkSessionResponse end(
            long staff, long sessionId, int version, String key, String requestId) {
        WindowWorkSessionEntity candidate = sessions.selectById(sessionId);
        if (candidate == null || !Objects.equals(candidate.getStaffUserId(), staff)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        scope(staff, candidate.getWindowId());
        return command(
                staff,
                "END_SESSION",
                key,
                sessionId + ":" + version,
                () -> {
                    WindowWorkSessionEntity session = active(staff, sessionId);
                    if (sessions.activeTicketCount(sessionId) != 0) throw conflict();
                    if (sessions.end(sessionId, staff, version) != 1
                            || sessions.release(sessionId, staff) != 1) throw conflict();
                    log(null, staff, QueueOperation.END_SESSION, null, null, requestId);
                    return sessionId;
                },
                id -> WorkSessionResponse.from(sessions.selectById(id)));
    }

    @Override
    public CurrentWorkSessionResponse currentSession(long staff, long window) {
        scope(staff, window);
        WindowWorkSessionEntity session = sessions.current(window, staff);
        if (session == null) return null;
        QueueTicketEntity ticket = tickets.currentBySession(session.getId());
        return new CurrentWorkSessionResponse(
                WorkSessionResponse.from(session),
                ticket == null ? null : QueueTicketResponse.from(ticket));
    }

    @Override
    public QueueTicketResponse callNext(
            long staff, long sessionId, List<Long> itemIds, String key, String requestId) {
        WindowWorkSessionEntity candidate = sessions.selectById(sessionId);
        if (candidate == null || !Objects.equals(candidate.getStaffUserId(), staff))
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        ResourceStaffClient.Scope scope = scope(staff, candidate.getWindowId());
        List<Long> allowedItems = items(scope);
        List<Long> selectedItems = allowedItems;
        if (itemIds != null && !itemIds.isEmpty()) {
            if (itemIds.size() > 50
                    || itemIds.stream()
                            .anyMatch(id -> id == null || id <= 0 || !allowedItems.contains(id))) {
                throw new BusinessException(CommonErrorCode.NOT_FOUND);
            }
            selectedItems = itemIds.stream().distinct().sorted().toList();
        }
        List<Long> selected = selectedItems;
        return command(
                staff,
                "CALL",
                key,
                sessionId + ":" + selected,
                () -> {
                    WindowWorkSessionEntity session = active(staff, sessionId);
                    if (sessions.activeTicketCount(sessionId) != 0) throw conflict();
                    if (selected.isEmpty()) {
                        log(null, staff, QueueOperation.CALL, null, null, requestId);
                        return null;
                    }
                    QueueTicketEntity ticket =
                            tickets.lockNext(session.getOutletId(), today(), selected);
                    if (ticket == null) {
                        log(null, staff, QueueOperation.CALL, null, null, requestId);
                        return null;
                    }
                    if (tickets.transition(
                                    ticket.getId(),
                                    "WAITING",
                                    "CALLED",
                                    ticket.getVersion(),
                                    session.getWindowId(),
                                    sessionId,
                                    null)
                            != 1) throw conflict();
                    log(
                            ticket,
                            staff,
                            QueueOperation.CALL,
                            QueueTicketStatus.WAITING,
                            QueueTicketStatus.CALLED,
                            requestId);
                    return ticket.getId();
                },
                id -> id == null ? null : QueueTicketResponse.from(tickets.selectById(id)));
    }

    @Override
    public QueueTicketResponse change(
            long staff,
            long sessionId,
            long ticketId,
            int version,
            String action,
            String result,
            String key,
            String requestId) {
        if (result != null && (result.length() > 64 || !"COMPLETE".equals(action))) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        WindowWorkSessionEntity candidate = sessions.selectById(sessionId);
        if (candidate == null || !Objects.equals(candidate.getStaffUserId(), staff))
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        ResourceStaffClient.Scope scope = scope(staff, candidate.getWindowId());
        String from =
                switch (action) {
                    case "COMPLETE" -> "SERVING";
                    case "RECALL", "MISS", "START" -> "CALLED";
                    default -> throw new BusinessException(CommonErrorCode.VALIDATION);
                };
        String to =
                switch (action) {
                    case "RECALL" -> "CALLED";
                    case "MISS" -> "MISSED";
                    case "START" -> "SERVING";
                    default -> "COMPLETED";
                };
        return command(
                staff,
                action,
                key,
                sessionId + ":" + ticketId + ":" + version + ":" + Objects.toString(result, ""),
                () -> {
                    WindowWorkSessionEntity session = active(staff, sessionId);
                    QueueTicketEntity ticket = tickets.selectById(ticketId);
                    if (ticket == null
                            || !Objects.equals(ticket.getOutletId(), session.getOutletId())
                            || !Objects.equals(ticket.getCalledWindowId(), session.getWindowId())
                            || !Objects.equals(ticket.getWorkSessionId(), sessionId)
                            || !items(scope).contains(ticket.getItemId()))
                        throw new BusinessException(CommonErrorCode.NOT_FOUND);
                    if (!from.equals(ticket.getStatus().name())
                            || tickets.transition(
                                            ticketId,
                                            from,
                                            to,
                                            version,
                                            session.getWindowId(),
                                            sessionId,
                                            result)
                                    != 1) throw conflict();
                    QueueOperation operation =
                            switch (action) {
                                case "RECALL" -> QueueOperation.RECALL;
                                case "MISS" -> QueueOperation.MARK_MISSED;
                                case "START" -> QueueOperation.START_SERVING;
                                default -> QueueOperation.COMPLETE;
                            };
                    log(
                            ticket,
                            staff,
                            operation,
                            QueueTicketStatus.valueOf(from),
                            QueueTicketStatus.valueOf(to),
                            requestId);
                    if (!"RECALL".equals(action))
                        sync.enqueue(IdWorker.getId(), ticketId, ticket.getAppointmentId(), to);
                    return ticketId;
                },
                id -> QueueTicketResponse.from(tickets.selectById(id)));
    }

    private WindowWorkSessionEntity active(long staff, long id) {
        WindowWorkSessionEntity session = sessions.active(id, staff);
        if (session == null) throw conflict();
        return session;
    }

    private void log(
            QueueTicketEntity ticket,
            long staff,
            QueueOperation operation,
            QueueTicketStatus from,
            QueueTicketStatus to,
            String requestId) {
        QueueOperationLogEntity log = new QueueOperationLogEntity();
        log.setId(IdWorker.getId());
        if (ticket != null) {
            log.setTicketId(ticket.getId());
            log.setAppointmentId(ticket.getAppointmentId());
        }
        log.setActorType(QueueActorType.STAFF);
        log.setActorId(staff);
        log.setOperation(operation);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setRequestId(requestId);
        log.setOccurredAt(Instant.now());
        logs.insert(log);
    }

    private <T> T command(
            long staff,
            String operation,
            String key,
            String payload,
            java.util.function.Supplier<Long> work,
            java.util.function.Function<Long, T> response) {
        if (key == null || key.isBlank() || key.length() > 128)
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_REQUIRED);
        String hash = hash(payload);
        try {
            Long resultId =
                    tx.execute(
                            status -> {
                                long commandId = IdWorker.getId();
                                commands.begin(commandId, staff, operation, key, hash);
                                Long result = work.get();
                                commands.finish(commandId, result);
                                return result;
                            });
            return response.apply(resultId);
        } catch (DuplicateKeyException duplicate) {
            QueueCommandMapper.Saved saved = commands.find(staff, operation, key);
            if (saved == null) {
                if ("START_SESSION".equals(operation)) {
                    throw new BusinessException(QueueErrorCode.SESSION_ACTIVE);
                }
                throw duplicate;
            }
            if (!hash.equals(saved.payloadHash()))
                throw new BusinessException(CommonErrorCode.IDEMPOTENCY_CONFLICT);
            return response.apply(saved.resultId());
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BusinessException conflict() {
        return new BusinessException(QueueErrorCode.STATE_CONFLICT);
    }

    private static LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    @Override
    public List<QueueProgressResponse> own(long user, Long appointment) {
        return tickets.ownCurrent(user, today(), appointment).stream().map(this::progress).toList();
    }

    @Override
    public QueueProgressResponse ownTicket(long user, long ticket) {
        QueueTicketEntity entity = tickets.selectById(ticket);
        if (entity == null || !Objects.equals(entity.getUserId(), user))
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return progress(entity);
    }

    private QueueProgressResponse progress(QueueTicketEntity ticket) {
        long ahead =
                ticket.getStatus() == QueueTicketStatus.WAITING
                        ? tickets.ahead(
                                ticket.getOutletId(),
                                ticket.getServiceDate(),
                                ticket.getItemId(),
                                ticket.getPriority(),
                                ticket.getCheckedInAt(),
                                ticket.getId())
                        : 0;
        QueueTicketEntity call = tickets.currentCall(ticket.getOutletId(), ticket.getServiceDate());
        String estimate =
                ticket.getStatus() == QueueTicketStatus.WAITING
                        ? "WAITING_ORDER_ONLY"
                        : ticket.getStatus().name();
        return new QueueProgressResponse(
                QueueTicketResponse.from(ticket),
                ahead,
                call == null ? null : call.getTicketNo(),
                estimate);
    }

    @Override
    public Object overview(long outlet, LocalDate date) {
        return Map.of(
                "outletId",
                Long.toString(outlet),
                "serviceDate",
                date.toString(),
                "groups",
                tickets.overview(outlet, date).stream()
                        .map(
                                row ->
                                        new QueueOverviewGroupResponse(
                                                row.itemId().toString(),
                                                row.windowId() == null
                                                        ? null
                                                        : row.windowId().toString(),
                                                row.status(),
                                                row.count()))
                        .toList());
    }
}
