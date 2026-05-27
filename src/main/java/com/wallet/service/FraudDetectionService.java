package com.wallet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("5000.00");
    private static final int MAX_TRANSACTIONS_PER_MINUTE = 10;

    // Simple in-memory velocity tracker (use Redis in production)
    private final Map<String, AtomicInteger> velocityMap = new ConcurrentHashMap<>();

    public void checkDeposit(String userId, BigDecimal amount) {
        checkVelocity(userId);
        if (amount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            log.warn("FRAUD_ALERT: High-value deposit detected. user={}, amount={}", userId, amount);
        }
    }

    public void checkWithdrawal(String userId, BigDecimal amount) {
        checkVelocity(userId);
        if (amount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            log.warn("FRAUD_ALERT: High-value withdrawal detected. user={}, amount={}", userId, amount);
        }
    }

    private void checkVelocity(String userId) {
        AtomicInteger count = velocityMap.computeIfAbsent(userId, k -> new AtomicInteger(0));
        int current = count.incrementAndGet();
        if (current > MAX_TRANSACTIONS_PER_MINUTE) {
            log.warn("FRAUD_ALERT: Velocity limit exceeded for user={}, count={}", userId, current);
        }
        // Reset periodically (simplified; use Redis TTL in production)
    }
}
