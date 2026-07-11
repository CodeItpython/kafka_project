package com.kafka.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Checkout / order microservice. Owns order persistence (its own Postgres DB) and
 * a MOCK payment step. At checkout it reads the authoritative cart from
 * shopping-service over Feign (forwarding the caller's JWT), records the order,
 * runs the mock payment, then clears the cart. No real payment gateway is wired —
 * that needs the merchant's credentials.
 */
@SpringBootApplication
@EnableFeignClients
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
