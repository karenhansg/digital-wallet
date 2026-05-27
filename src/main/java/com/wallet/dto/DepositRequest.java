package com.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DepositRequest(
        @NotNull @DecimalMin(value = "0.01", message = "Amount must be positive")
        BigDecimal amount,
        @NotBlank(message = "Idempotency key is required")
        String idempotencyKey,
        String description
) {}
