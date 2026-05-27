package com.wallet.controller;

import com.wallet.dto.*;
import com.wallet.entity.TransactionStatus;
import com.wallet.entity.TransactionType;
import com.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/wallet")
@Tag(name = "Wallet", description = "Wallet management and balance operations")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    @Operation(summary = "Create a new wallet for the authenticated user")
    public ResponseEntity<WalletResponse> createWallet(Authentication auth) {
        WalletResponse response = walletService.createWallet(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get wallet details for the authenticated user")
    public ResponseEntity<WalletResponse> getWallet(Authentication auth) {
        return ResponseEntity.ok(walletService.getWallet(auth.getName()));
    }

    @PostMapping("/deposit")
    @Operation(summary = "Deposit funds into wallet")
    public ResponseEntity<TransactionResponse> deposit(
            Authentication auth,
            @Valid @RequestBody DepositRequest request,
            HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        TransactionResponse response = walletService.deposit(auth.getName(), request, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw funds from wallet (requires OTP)")
    public ResponseEntity<TransactionResponse> withdraw(
            Authentication auth,
            @Valid @RequestBody WithdrawRequest request,
            HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        TransactionResponse response = walletService.withdraw(auth.getName(), request, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/transactions")
    @Operation(summary = "List transactions with pagination and filters")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            Authentication auth,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(walletService.getTransactions(
                auth.getName(), type, status, from, to, pageable));
    }
}
