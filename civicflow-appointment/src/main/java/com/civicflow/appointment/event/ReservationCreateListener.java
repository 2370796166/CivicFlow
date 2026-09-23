package com.civicflow.appointment.event;

import com.civicflow.appointment.entity.AppointmentReservationRequestEntity;
import com.civicflow.appointment.enums.ReservationRequestStatus;
import com.civicflow.appointment.enums.StockReleaseReason;
import com.civicflow.appointment.error.AppointmentErrorCode;
import com.civicflow.appointment.mapper.AppointmentReservationRequestMapper;
import com.civicflow.appointment.service.AppointmentCreationService;
import com.civicflow.appointment.service.ReservationCreationRejectedException;
import com.civicflow.appointment.service.ReservationRedisStateService;
import com.civicflow.appointment.service.StockReleaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
public class ReservationCreateListener {
    private final ObjectMapper objectMapper;
    private final AppointmentCreationService creationService;
    private final ReservationRedisStateService redisStateService;
    private final StockReleaseService stockReleaseService;
    private final AppointmentReservationRequestMapper requestMapper;

    public ReservationCreateListener(
            ObjectMapper objectMapper,
            AppointmentCreationService creationService,
            ReservationRedisStateService redisStateService,
            StockReleaseService stockReleaseService,
            AppointmentReservationRequestMapper requestMapper) {
        this.objectMapper = objectMapper;
        this.creationService = creationService;
        this.redisStateService = redisStateService;
        this.stockReleaseService = stockReleaseService;
        this.requestMapper = requestMapper;
    }

    @RabbitListener(
            queues = AppointmentMessaging.RESERVATION_CREATE_QUEUE,
            containerFactory = "reservationListenerContainerFactory",
            ackMode = "MANUAL")
    public void consume(Message message, Channel channel) throws IOException {
        ReservationRequestedEvent event = read(message);
        try {
            AppointmentCreationService.CreationResult result =
                    creationService.create(event, ReservationEventHash.compute(event));
            if (!redisStateService.markPersisted(result.request(), result.appointmentId())) {
                throw new IllegalStateException("Redis reservation persistence marker failed");
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (ReservationCreationRejectedException exception) {
            compensateAndReject(
                    exception.request(),
                    StockReleaseReason.BUSINESS_REJECTED,
                    exception.failureCode(),
                    exception);
        } catch (DuplicateKeyException exception) {
            AppointmentReservationRequestEntity request =
                    creationService.findRequest(event.payload().reservationId());
            if (request == null) {
                throw new PermanentReservationMessageException(
                        "Unique conflict without reservation request", exception);
            }
            Long appointmentId = creationService.findAppointmentId(request.getReservationId());
            if (appointmentId != null) {
                if (!redisStateService.markPersisted(request, appointmentId)) {
                    throw new IllegalStateException(
                            "Redis reservation persistence marker failed after redelivery");
                }
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            compensateAndReject(
                    request,
                    StockReleaseReason.DUPLICATE_GUARD_REJECTED,
                    AppointmentErrorCode.DUP_ACTIVE.code(),
                    exception);
        }
    }

    private void compensateAndReject(
            AppointmentReservationRequestEntity request,
            StockReleaseReason reason,
            String failureCode,
            RuntimeException cause) {
        stockReleaseService.release(request, reason);
        request.setStatus(ReservationRequestStatus.FAILED);
        request.setFailureCode(failureCode);
        request.setLastErrorCode(failureCode);
        requestMapper.updateById(request);
        throw new PermanentReservationMessageException(
                "Reservation creation was permanently rejected after compensation", cause);
    }

    private ReservationRequestedEvent read(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), ReservationRequestedEvent.class);
        } catch (IOException exception) {
            throw new PermanentReservationMessageException(
                    "Reservation message JSON is invalid", exception);
        }
    }
}
