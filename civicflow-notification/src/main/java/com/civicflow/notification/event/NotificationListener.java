package com.civicflow.notification.event;

import com.civicflow.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {
    private final NotificationService service;
    private final ObjectMapper mapper;

    public NotificationListener(NotificationService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @RabbitListener(queues = NotificationMessaging.IN_APP_QUEUE)
    public void receive(byte[] body) {
        if (body.length > 4096)
            throw new AmqpRejectAndDontRequeueException("Notification event too large");
        try {
            String json = new String(body, StandardCharsets.UTF_8);
            service.consume(mapper.readValue(json, NotificationEvent.class), json);
        } catch (IllegalArgumentException
                | com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid notification event", exception);
        }
    }
}
