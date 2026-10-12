package com.civicflow.queue.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

@Configuration
public class QueueRabbitObservabilityConfig {
    @Bean("checkInListenerContainerFactory")
    SimpleRabbitListenerContainerFactory checkInListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MeterRegistry metrics) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        Advice retry =
                RetryInterceptorBuilder.stateless()
                        .retryPolicy(new SimpleRetryPolicy(5))
                        .recoverer(
                                (message, cause) -> {
                                    metrics.counter(
                                                    "civicflow_mq_consumer_dlq",
                                                    "consumer",
                                                    "checkin")
                                            .increment();
                                    throw new AmqpRejectAndDontRequeueException(
                                            "Check-in message rejected after bounded retry",
                                            true,
                                            null);
                                })
                        .build();
        factory.setAdviceChain(retry);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
