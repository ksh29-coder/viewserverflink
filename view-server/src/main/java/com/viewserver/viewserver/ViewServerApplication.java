package com.viewserver.viewserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Main Spring Boot application for the View Server.
 * Phase 1: Kafka consumers + Redis cache + REST APIs
 * Phase 2: Will add Flink aggregation and Kafka Streams processing
 */
@SpringBootApplication(scanBasePackages = {"com.viewserver.viewserver", "com.viewserver.data", "com.viewserver.aggregation"})
@EnableKafka
public class ViewServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ViewServerApplication.class, args);
    }
} 