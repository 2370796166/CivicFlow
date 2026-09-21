package com.civicflow.appointment.config;

import com.civicflow.appointment.support.RedisKeyFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppointmentProperties.class)
public class AppointmentRedisConfig {
    @Bean
    RedisKeyFactory redisKeyFactory(AppointmentProperties properties) {
        return new RedisKeyFactory(properties.getEnvironment());
    }
}
