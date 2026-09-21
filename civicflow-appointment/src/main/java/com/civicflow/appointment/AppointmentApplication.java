package com.civicflow.appointment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableFeignClients
@EnableScheduling
@SpringBootApplication
@MapperScan("com.civicflow.appointment.mapper")
public class AppointmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppointmentApplication.class, args);
    }
}
