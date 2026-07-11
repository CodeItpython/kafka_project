package com.kafka.order.payment;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * MOCK payment gateway. There is no real PG integration — that requires the
 * merchant's credentials and an external contract. This stand-in always approves
 * and returns a fake transaction id, so the checkout flow is exercisable end-to-end.
 * A real integration (KakaoPay/Toss/PG) would replace {@link #charge}.
 */
@Service
@Slf4j
public class MockPaymentService {
    public static final String METHOD = "MOCK_PAY";

    public PaymentResult charge(long amount, String orderNumber) {
        String transactionId = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[MOCK] approved payment {} for order {} amount {}", transactionId, orderNumber, amount);
        return new PaymentResult("PAID", METHOD, transactionId);
    }

    public record PaymentResult(String status, String method, String transactionId) {}
}
