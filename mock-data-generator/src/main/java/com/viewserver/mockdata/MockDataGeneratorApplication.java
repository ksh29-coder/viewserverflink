package com.viewserver.mockdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application for the Mock Data Generator.
 * This service generates realistic financial data and publishes it to Kafka topics.
 */
@SpringBootApplication(scanBasePackages = {"com.viewserver.mockdata", "com.viewserver.data"})
@EnableScheduling
public class MockDataGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockDataGeneratorApplication.class, args);
    }
} 