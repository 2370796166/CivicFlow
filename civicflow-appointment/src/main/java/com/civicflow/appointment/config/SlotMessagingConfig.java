package com.civicflow.appointment.config;

import com.civicflow.appointment.event.AppointmentMessaging;
import com.civicflow.appointment.event.SlotMessaging;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlotMessagingConfig {
    @Bean
    TopicExchange slotExchange() {
        return new TopicExchange(SlotMessaging.EXCHANGE, true, false);
    }

    @Bean
    TopicExchange slotRetryExchange() {
        return new TopicExchange(SlotMessaging.RETRY_EXCHANGE, true, false);
    }

    @Bean
    Queue slotConfigurationQueue() {
        return QueueBuilder.durable(SlotMessaging.QUEUE)
                .deadLetterExchange(SlotMessaging.RETRY_EXCHANGE)
                .deadLetterRoutingKey(SlotMessaging.RETRY_ROUTING)
                .build();
    }

    @Bean
    Queue slotConfigurationRetryQueue() {
        return QueueBuilder.durable(SlotMessaging.RETRY_QUEUE)
                .ttl(5000)
                .deadLetterExchange(SlotMessaging.EXCHANGE)
                .deadLetterRoutingKey(SlotMessaging.ROUTING)
                .build();
    }

    @Bean
    Queue slotConfigurationDeadLetterQueue() {
        return QueueBuilder.durable(SlotMessaging.DEAD_QUEUE).build();
    }

    @Bean
    Binding slotBinding(Queue slotConfigurationQueue, TopicExchange slotExchange) {
        return BindingBuilder.bind(slotConfigurationQueue)
                .to(slotExchange)
                .with(SlotMessaging.ROUTING);
    }

    @Bean
    Binding slotRetryBinding(Queue slotConfigurationRetryQueue, TopicExchange slotRetryExchange) {
        return BindingBuilder.bind(slotConfigurationRetryQueue)
                .to(slotRetryExchange)
                .with(SlotMessaging.RETRY_ROUTING);
    }

    @Bean
    Binding slotDeadBinding(Queue slotConfigurationDeadLetterQueue) {
        return BindingBuilder.bind(slotConfigurationDeadLetterQueue)
                .to(new TopicExchange(AppointmentMessaging.DEAD_LETTER_EXCHANGE, true, false))
                .with(SlotMessaging.DEAD_ROUTING);
    }

    @Bean("slotListenerContainerFactory")
    SimpleRabbitListenerContainerFactory slotListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
