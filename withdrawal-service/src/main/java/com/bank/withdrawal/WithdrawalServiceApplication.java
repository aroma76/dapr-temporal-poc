package com.bank.withdrawal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * What it does: Spring Boot bootstrap entry point for the Withdrawal Service.
 * Why it exists: Launches the Spring application context, starts embedded Tomcat on port 8081,
 *                and triggers TemporalConfig to initialize Temporal workers.
 * Technology used: Spring Boot (@SpringBootApplication).
 * Communication: Bootstraps all controllers, services, and Temporal workers.
 */
@SpringBootApplication
public class WithdrawalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WithdrawalServiceApplication.class, args);
    }
}

