package com.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record WithdrawRequest(
        @NotNull @DecimalMin(value = "0.01", message = "Amount must be positive")
        BigDecimal amount,
        @NotBlank(message = "Idempotency key is required")
        String idempotencyKey,
        @NotBlank(message = "OTP code is required for withdrawals")
        String otpCode,
        String description
) {}
