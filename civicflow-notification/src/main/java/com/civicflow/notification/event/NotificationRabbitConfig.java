package com.civicflow.notification.event;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationRabbitConfig {
    @Bean
    TopicExchange notificationAppointmentExchange() {
        return new TopicExchange(NotificationMessaging.APPOINTMENT_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange notificationQueueExchange() {
        return new TopicExchange(NotificationMessaging.QUEUE_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange notificationDeadExchange() {
        return new TopicExchange(NotificationMessaging.DEAD_EXCHANGE, true, false);
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(NotificationMessaging.IN_APP_QUEUE)
                .deadLetterExchange(NotificationMessaging.DEAD_EXCHANGE)
                .deadLetterRoutingKey(NotificationMessaging.IN_APP_DEAD)
                .build();
    }

    @Bean
    Queue notificationDeadQueue() {
        return QueueBuilder.durable(NotificationMessaging.IN_APP_QUEUE + ".dlq").build();
    }

    @Bean
    Binding appointmentNotifications(
            Queue notificationQueue, TopicExchange notificationAppointmentExchange) {
        return BindingBuilder.bind(notificationQueue)
                .to(notificationAppointmentExchange)
                .with(NotificationMessaging.APPOINTMENT_PATTERN);
    }

    @Bean
    Binding queueNotifications(Queue notificationQueue, TopicExchange notificationQueueExchange) {
        return BindingBuilder.bind(notificationQueue)
                .to(notificationQueueExchange)
                .with(NotificationMessaging.QUEUE_PATTERN);
    }

    @Bean
    Binding notificationDeadBinding(
            Queue notificationDeadQueue, TopicExchange notificationDeadExchange) {
        return BindingBuilder.bind(notificationDeadQueue)
                .to(notificationDeadExchange)
                .with(NotificationMessaging.IN_APP_DEAD);
    }
}
