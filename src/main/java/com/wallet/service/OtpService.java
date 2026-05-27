package com.wallet.service;

import com.wallet.entity.OtpToken;
import com.wallet.exception.InvalidOtpException;
import com.wallet.repository.OtpTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpTokenRepository otpTokenRepository;

    @Value("${otp.expiry-seconds}")
    private int otpExpirySeconds;

    public OtpService(OtpTokenRepository otpTokenRepository) {
        this.otpTokenRepository = otpTokenRepository;
    }

    @Transactional
    public String generateOtp(String userId, String purpose) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        OtpToken token = new OtpToken();
        token.setUserId(userId);
        token.setCode(code);
        token.setPurpose(purpose);
        token.setExpiresAt(Instant.now().plusSeconds(otpExpirySeconds));
        otpTokenRepository.save(token);
        log.info("OTP generated for user={}, purpose={}", userId, purpose);
        // In production: send via SMS/email. Here we return it directly for testing.
        return code;
    }

    @Transactional
    public void verifyOtp(String userId, String code, String purpose) {
        OtpToken token = otpTokenRepository
                .findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(userId, purpose)
                .orElseThrow(() -> new InvalidOtpException("No valid OTP found"));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidOtpException("OTP has expired");
        }
        if (!token.getCode().equals(code)) {
            throw new InvalidOtpException("Invalid OTP code");
        }

        token.setUsed(true);
        otpTokenRepository.save(token);
        log.info("OTP verified for user={}, purpose={}", userId, purpose);
    }
}
