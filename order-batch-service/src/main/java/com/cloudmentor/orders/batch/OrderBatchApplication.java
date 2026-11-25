package com.cloudmentor.orders.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Spring Boot 3 + Spring Batch Financial Order Settlement Service.
 */
@SpringBootApplication
@EnableScheduling
public class OrderBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderBatchApplication.class, args);
    }
}
