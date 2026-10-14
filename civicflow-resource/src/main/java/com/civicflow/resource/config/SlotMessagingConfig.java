package com.civicflow.resource.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlotMessagingConfig {
    public static final String EXCHANGE = "cf.resource.x";

    @Bean
    TopicExchange resourceSlotExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }
}
