package com.wallet.service;

import com.wallet.dto.DepositRequest;
import com.wallet.dto.TransactionResponse;
import com.wallet.dto.WalletResponse;
import com.wallet.dto.WithdrawRequest;
import com.wallet.entity.*;
import com.wallet.exception.*;
import com.wallet.repository.TransactionRepository;
import com.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private OtpService otpService;
    @Mock private AuditService auditService;
    @Mock private FraudDetectionService fraudDetectionService;

    @InjectMocks private WalletService walletService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(walletService, "dailyDepositLimit", new BigDecimal("10000"));
        ReflectionTestUtils.setField(walletService, "dailyWithdrawalLimit", new BigDecimal("5000"));
        ReflectionTestUtils.setField(walletService, "weeklyDepositLimit", new BigDecimal("50000"));
        ReflectionTestUtils.setField(walletService, "weeklyWithdrawalLimit", new BigDecimal("25000"));
        ReflectionTestUtils.setField(walletService, "maxBalance", new BigDecimal("100000"));
    }

    @Test
    void createWallet_success() {
        when(walletRepository.existsByUserId("user1")).thenReturn(false);
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> {
            Wallet w = inv.getArgument(0);
            w.setId(UUID.randomUUID());
            return w;
        });

        WalletResponse response = walletService.createWallet("user1");

        assertNotNull(response);
        assertEquals("user1", response.userId());
        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    void createWallet_duplicate_throwsException() {
        when(walletRepository.existsByUserId("user1")).thenReturn(true);
        assertThrows(DuplicateWalletException.class, () -> walletService.createWallet("user1"));
    }

    @Test
    void deposit_success() {
        Wallet wallet = createTestWallet("user1", new BigDecimal("1000"));
        when(walletRepository.findByUserId("user1")).thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(transactionRepository.sumAmountByTypeAndPeriod(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });

        DepositRequest request = new DepositRequest(new BigDecimal("500"), "key-1", "Test deposit");
        TransactionResponse response = walletService.deposit("user1", request, "127.0.0.1");

        assertNotNull(response);
        assertEquals(TransactionType.DEPOSIT, response.type());
        assertEquals(new BigDecimal("500"), response.amount());
    }

    @Test
    void deposit_idempotent_returnsSameResult() {
        Transaction existing = new Transaction();
        existing.setId(UUID.randomUUID());
        existing.setType(TransactionType.DEPOSIT);
        existing.setAmount(new BigDecimal("500"));
        existing.setBalanceBefore(BigDecimal.ZERO);
        existing.setBalanceAfter(new BigDecimal("500"));
        existing.setStatus(TransactionStatus.COMPLETED);

        when(transactionRepository.findByIdempotencyKey("dup-key")).thenReturn(Optional.of(existing));

        DepositRequest request = new DepositRequest(new BigDecimal("500"), "dup-key", "Test");
        TransactionResponse response = walletService.deposit("user1", request, "127.0.0.1");

        assertEquals(existing.getId(), response.id());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void withdraw_insufficientBalance_throwsException() {
        Wallet wallet = createTestWallet("user1", new BigDecimal("100"));
        when(walletRepository.findByUserId("user1")).thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(transactionRepository.sumAmountByTypeAndPeriod(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        WithdrawRequest request = new WithdrawRequest(new BigDecimal("500"), "key-2", "123456", "Test");
        assertThrows(InsufficientBalanceException.class,
                () -> walletService.withdraw("user1", request, "127.0.0.1"));
    }

    @Test
    void deposit_exceedsDailyLimit_throwsException() {
        Wallet wallet = createTestWallet("user1", new BigDecimal("1000"));
        when(walletRepository.findByUserId("user1")).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(transactionRepository.sumAmountByTypeAndPeriod(any(), eq(TransactionType.DEPOSIT), any()))
                .thenReturn(new BigDecimal("9500"));

        DepositRequest request = new DepositRequest(new BigDecimal("600"), "key-3", "Test");
        assertThrows(TransactionLimitExceededException.class,
                () -> walletService.deposit("user1", request, "127.0.0.1"));
    }

    private Wallet createTestWallet(String userId, BigDecimal balance) {
        Wallet wallet = new Wallet();
        wallet.setId(UUID.randomUUID());
        wallet.setUserId(userId);
        wallet.setBalance(balance);
        wallet.setStatus(WalletStatus.ACTIVE);
        return wallet;
    }
}
