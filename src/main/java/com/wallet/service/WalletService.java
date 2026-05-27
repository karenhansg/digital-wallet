package com.wallet.service;

import com.wallet.dto.*;
import com.wallet.entity.*;
import com.wallet.exception.*;
import com.wallet.repository.TransactionRepository;
import com.wallet.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final OtpService otpService;
    private final AuditService auditService;
    private final FraudDetectionService fraudDetectionService;

    @Value("${wallet.daily-deposit-limit}")
    private BigDecimal dailyDepositLimit;

    @Value("${wallet.daily-withdrawal-limit}")
    private BigDecimal dailyWithdrawalLimit;

    @Value("${wallet.weekly-deposit-limit}")
    private BigDecimal weeklyDepositLimit;

    @Value("${wallet.weekly-withdrawal-limit}")
    private BigDecimal weeklyWithdrawalLimit;

    @Value("${wallet.max-balance}")
    private BigDecimal maxBalance;

    public WalletService(WalletRepository walletRepository,
                         TransactionRepository transactionRepository,
                         OtpService otpService,
                         AuditService auditService,
                         FraudDetectionService fraudDetectionService) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.otpService = otpService;
        this.auditService = auditService;
        this.fraudDetectionService = fraudDetectionService;
    }

    @Transactional
    public WalletResponse createWallet(String userId) {
        if (walletRepository.existsByUserId(userId)) {
            throw new DuplicateWalletException("Wallet already exists for user: " + userId);
        }
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet = walletRepository.save(wallet);
        auditService.log(userId, "WALLET_CREATED", "WALLET", wallet.getId().toString(), null, null);
        log.info("Wallet created for user={}", userId);
        return WalletResponse.from(wallet);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(String userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));
        return WalletResponse.from(wallet);
    }

    @Transactional
    public TransactionResponse deposit(String userId, DepositRequest request, String ipAddress) {
        // Idempotency check
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Duplicate deposit detected, idempotencyKey={}", request.idempotencyKey());
            return TransactionResponse.from(existing.get());
        }

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        validateWalletActive(wallet);
        enforceLimits(wallet.getId(), TransactionType.DEPOSIT, request.amount());
        fraudDetectionService.checkDeposit(userId, request.amount());

        BigDecimal newBalance = wallet.getBalance().add(request.amount());
        if (newBalance.compareTo(maxBalance) > 0) {
            throw new TransactionLimitExceededException("Deposit would exceed maximum wallet balance");
        }

        // Pessimistic lock for balance update
        Wallet lockedWallet = walletRepository.findByIdForUpdate(wallet.getId())
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found"));

        BigDecimal balanceBefore = lockedWallet.getBalance();
        lockedWallet.setBalance(balanceBefore.add(request.amount()));
        walletRepository.save(lockedWallet);

        Transaction tx = new Transaction();
        tx.setWalletId(lockedWallet.getId());
        tx.setIdempotencyKey(request.idempotencyKey());
        tx.setType(TransactionType.DEPOSIT);
        tx.setAmount(request.amount());
        tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(lockedWallet.getBalance());
        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setDescription(request.description());
        tx.setReferenceId(UUID.randomUUID().toString());
        tx = transactionRepository.save(tx);

        auditService.log(userId, "DEPOSIT", "TRANSACTION", tx.getId().toString(),
                "amount=" + request.amount(), ipAddress);
        log.info("Deposit completed: user={}, amount={}, txId={}", userId, request.amount(), tx.getId());
        return TransactionResponse.from(tx);
    }

    @Transactional
    public TransactionResponse withdraw(String userId, WithdrawRequest request, String ipAddress) {
        // OTP verification for withdrawals
        otpService.verifyOtp(userId, request.otpCode(), "WITHDRAWAL");

        // Idempotency check
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Duplicate withdrawal detected, idempotencyKey={}", request.idempotencyKey());
            return TransactionResponse.from(existing.get());
        }

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        validateWalletActive(wallet);
        enforceLimits(wallet.getId(), TransactionType.WITHDRAWAL, request.amount());
        fraudDetectionService.checkWithdrawal(userId, request.amount());

        // Pessimistic lock for balance update
        Wallet lockedWallet = walletRepository.findByIdForUpdate(wallet.getId())
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found"));

        BigDecimal balanceBefore = lockedWallet.getBalance();
        if (balanceBefore.compareTo(request.amount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance. Available: " + balanceBefore);
        }

        lockedWallet.setBalance(balanceBefore.subtract(request.amount()));
        walletRepository.save(lockedWallet);

        Transaction tx = new Transaction();
        tx.setWalletId(lockedWallet.getId());
        tx.setIdempotencyKey(request.idempotencyKey());
        tx.setType(TransactionType.WITHDRAWAL);
        tx.setAmount(request.amount());
        tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(lockedWallet.getBalance());
        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setDescription(request.description());
        tx.setReferenceId(UUID.randomUUID().toString());
        tx = transactionRepository.save(tx);

        auditService.log(userId, "WITHDRAWAL", "TRANSACTION", tx.getId().toString(),
                "amount=" + request.amount(), ipAddress);
        log.info("Withdrawal completed: user={}, amount={}, txId={}", userId, request.amount(), tx.getId());
        return TransactionResponse.from(tx);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(String userId, TransactionType type,
                                                     TransactionStatus status,
                                                     Instant from, Instant to, Pageable pageable) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));
        return transactionRepository.findByFilters(wallet.getId(), type, status, from, to, pageable)
                .map(TransactionResponse::from);
    }

    private void validateWalletActive(Wallet wallet) {
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new IllegalStateException("Wallet is not active. Status: " + wallet.getStatus());
        }
    }

    private void enforceLimits(UUID walletId, TransactionType type, BigDecimal amount) {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant startOfWeek = Instant.now().minus(7, ChronoUnit.DAYS);

        BigDecimal dailyTotal = transactionRepository.sumAmountByTypeAndPeriod(walletId, type, startOfDay);
        BigDecimal weeklyTotal = transactionRepository.sumAmountByTypeAndPeriod(walletId, type, startOfWeek);

        BigDecimal dailyLimit = type == TransactionType.DEPOSIT ? dailyDepositLimit : dailyWithdrawalLimit;
        BigDecimal weeklyLimit = type == TransactionType.DEPOSIT ? weeklyDepositLimit : weeklyWithdrawalLimit;

        if (dailyTotal.add(amount).compareTo(dailyLimit) > 0) {
            throw new TransactionLimitExceededException(
                    "Daily " + type + " limit exceeded. Limit: " + dailyLimit + ", Used: " + dailyTotal);
        }
        if (weeklyTotal.add(amount).compareTo(weeklyLimit) > 0) {
            throw new TransactionLimitExceededException(
                    "Weekly " + type + " limit exceeded. Limit: " + weeklyLimit + ", Used: " + weeklyTotal);
        }
    }
}
