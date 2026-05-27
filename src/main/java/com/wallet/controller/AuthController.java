package com.wallet.controller;

import com.wallet.security.JwtUtil;
import com.wallet.service.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Tag(name = "Auth & OTP", description = "Authentication and OTP operations")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final OtpService otpService;

    public AuthController(JwtUtil jwtUtil, OtpService otpService) {
        this.jwtUtil = jwtUtil;
        this.otpService = otpService;
    }

    @PostMapping("/api/auth/token")
    @Operation(summary = "Generate a JWT token for testing (mock identity service)")
    public ResponseEntity<Map<String, String>> generateToken(@RequestParam String userId) {
        String token = jwtUtil.generateToken(userId);
        return ResponseEntity.ok(Map.of("token", token, "type", "Bearer"));
    }

    @PostMapping("/api/wallet/otp")
    @Operation(summary = "Request an OTP for withdrawal")
    public ResponseEntity<Map<String, String>> requestOtp(Authentication auth) {
        String code = otpService.generateOtp(auth.getName(), "WITHDRAWAL");
        // In production, OTP would be sent via SMS/email, not returned in response
        return ResponseEntity.ok(Map.of("message", "OTP sent", "otp", code));
    }
}
