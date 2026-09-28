package com.civicflow.appointment.config;

import com.civicflow.appointment.event.AppointmentMessaging;
import com.civicflow.appointment.event.PermanentReservationMessageException;
import java.util.HashMap;
import java.util.Map;
import org.aopalliance.aop.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

@Configuration
public class AppointmentRabbitConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppointmentRabbitConfig.class);

    @Bean
    TopicExchange appointmentExchange() {
        return new TopicExchange(AppointmentMessaging.APPOINTMENT_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange appointmentDeadLetterExchange() {
        return new TopicExchange(AppointmentMessaging.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange timeoutExchange() {
        return new TopicExchange(AppointmentMessaging.TIMEOUT_EXCHANGE, true, false);
    }

    @Bean
    Queue confirmDelayQueue() {
        return QueueBuilder.durable(AppointmentMessaging.TIMEOUT_DELAY_QUEUE)
                .ttl(300_000)
                .deadLetterExchange(AppointmentMessaging.TIMEOUT_EXCHANGE)
                .deadLetterRoutingKey(AppointmentMessaging.TIMEOUT_CHECK_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue confirmTimeoutQueue() {
        return QueueBuilder.durable(AppointmentMessaging.TIMEOUT_CHECK_QUEUE)
                .deadLetterExchange(AppointmentMessaging.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(AppointmentMessaging.TIMEOUT_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue confirmTimeoutDeadLetterQueue() {
        return QueueBuilder.durable(AppointmentMessaging.TIMEOUT_CHECK_DLQ).build();
    }

    @Bean
    Binding confirmDelayBinding(Queue confirmDelayQueue, TopicExchange timeoutExchange) {
        return BindingBuilder.bind(confirmDelayQueue)
                .to(timeoutExchange)
                .with(AppointmentMessaging.TIMEOUT_SCHEDULE_ROUTING_KEY);
    }

    @Bean
    Binding confirmTimeoutBinding(Queue confirmTimeoutQueue, TopicExchange timeoutExchange) {
        return BindingBuilder.bind(confirmTimeoutQueue)
                .to(timeoutExchange)
                .with(AppointmentMessaging.TIMEOUT_CHECK_ROUTING_KEY);
    }

    @Bean
    Binding confirmTimeoutDeadLetterBinding(
            Queue confirmTimeoutDeadLetterQueue, TopicExchange appointmentDeadLetterExchange) {
        return BindingBuilder.bind(confirmTimeoutDeadLetterQueue)
                .to(appointmentDeadLetterExchange)
                .with(AppointmentMessaging.TIMEOUT_DLQ_ROUTING_KEY);
    }

    @Bean
    Queue reservationCreateQueue() {
        return QueueBuilder.durable(AppointmentMessaging.RESERVATION_CREATE_QUEUE)
                .deadLetterExchange(AppointmentMessaging.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(AppointmentMessaging.RESERVATION_CREATE_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue reservationCreateDeadLetterQueue() {
        return QueueBuilder.durable(AppointmentMessaging.RESERVATION_CREATE_DLQ).build();
    }

    @Bean
    Binding reservationCreateBinding(
            Queue reservationCreateQueue, TopicExchange appointmentExchange) {
        return BindingBuilder.bind(reservationCreateQueue)
                .to(appointmentExchange)
                .with(AppointmentMessaging.RESERVATION_REQUESTED_ROUTING_KEY);
    }

    @Bean
    Binding reservationCreateDeadLetterBinding(
            Queue reservationCreateDeadLetterQueue, TopicExchange appointmentDeadLetterExchange) {
        return BindingBuilder.bind(reservationCreateDeadLetterQueue)
                .to(appointmentDeadLetterExchange)
                .with(AppointmentMessaging.RESERVATION_CREATE_DLQ_ROUTING_KEY);
    }

    @Bean("reservationListenerContainerFactory")
    SimpleRabbitListenerContainerFactory reservationListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory,
            AppointmentProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        Map<Class<? extends Throwable>, Boolean> retryable = new HashMap<>();
        retryable.put(PermanentReservationMessageException.class, false);
        retryable.put(Exception.class, true);
        SimpleRetryPolicy retryPolicy =
                new SimpleRetryPolicy(
                        properties.getReservation().getConsumerMaxAttempts(), retryable, true);
        Advice retry =
                RetryInterceptorBuilder.stateless()
                        .retryPolicy(retryPolicy)
                        .recoverer(
                                (message, cause) -> {
                                    // The default recoverer logs body/headers and does not reject
                                    // unacked MANUAL deliveries. The cause may contain sensitive
                                    // data.
                                    LOGGER.warn(
                                            "Message rejected after bounded retry type={}",
                                            cause.getClass().getSimpleName());
                                    throw new AmqpRejectAndDontRequeueException(
                                            "Message rejected after bounded retry", true, null);
                                })
                        .build();
        factory.setAdviceChain(retry);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
