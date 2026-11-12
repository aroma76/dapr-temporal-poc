package com.bank.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * What it does: Spring Boot bootstrap entry point for the Transaction Service.
 * Why it exists: Launches Spring context, connects to YugabyteDB via Spring Data JPA,
 *                and starts embedded Tomcat on port 8082.
 * Technology used: Spring Boot (@SpringBootApplication).
 * Communication: Bootstraps all JPA repositories, entities, services, and REST subscribers.
 */
@SpringBootApplication
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}

