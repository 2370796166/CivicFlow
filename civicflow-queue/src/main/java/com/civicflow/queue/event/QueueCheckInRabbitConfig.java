package com.civicflow.queue.event;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueCheckInRabbitConfig {
    public static final String EXCHANGE = "cf.appointment.x";
    public static final String ROUTING = "appointment.check-in.claimed.v1";
    public static final String QUEUE = "cf.queue.checkin-claim.q";
    public static final String DLX = "cf.dlx";
    public static final String DLQ = "cf.queue.checkin-claim.q.dlq";
    public static final String DEAD_ROUTING = "cf.queue.checkin-claim.q.dead";

    @Bean
    TopicExchange checkInAppointmentExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue checkInClaimQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DEAD_ROUTING)
                .build();
    }

    @Bean
    TopicExchange checkInDeadLetterExchange() {
        return new TopicExchange(DLX, true, false);
    }

    @Bean
    Queue checkInClaimDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding checkInDeadLetterBinding(
            Queue checkInClaimDeadLetterQueue, TopicExchange checkInDeadLetterExchange) {
        return BindingBuilder.bind(checkInClaimDeadLetterQueue)
                .to(checkInDeadLetterExchange)
                .with(DEAD_ROUTING);
    }

    @Bean
    Binding checkInClaimBinding(Queue checkInClaimQueue, TopicExchange checkInAppointmentExchange) {
        return BindingBuilder.bind(checkInClaimQueue).to(checkInAppointmentExchange).with(ROUTING);
    }
}
