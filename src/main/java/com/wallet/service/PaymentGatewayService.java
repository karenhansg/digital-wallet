package com.wallet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentGatewayService {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayService.class);

    public record PaymentResult(boolean success, String referenceId, String message) {}

    public PaymentResult processDeposit(String userId, BigDecimal amount) {
        log.info("Mock: Processing deposit from bank for user={}, amount={}", userId, amount);
        // Simulate external bank transfer success
        return new PaymentResult(true, "DEP-" + UUID.randomUUID(), "Bank transfer received");
    }

    public PaymentResult processPayout(String userId, BigDecimal amount) {
        log.info("Mock: Processing payout to bank for user={}, amount={}", userId, amount);
        // Simulate external payout success
        return new PaymentResult(true, "PAY-" + UUID.randomUUID(), "Payout initiated");
    }
}
