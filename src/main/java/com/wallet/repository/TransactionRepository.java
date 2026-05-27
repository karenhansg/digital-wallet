package com.wallet.repository;

import com.wallet.entity.Transaction;
import com.wallet.entity.TransactionStatus;
import com.wallet.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT t FROM Transaction t WHERE t.walletId = :walletId " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (CAST(:from AS timestamp) IS NULL OR t.createdAt >= :from) " +
           "AND (CAST(:to AS timestamp) IS NULL OR t.createdAt <= :to) " +
           "ORDER BY t.createdAt DESC")
    Page<Transaction> findByFilters(
            @Param("walletId") UUID walletId,
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.walletId = :walletId AND t.type = :type " +
           "AND t.status = 'COMPLETED' AND t.createdAt >= :since")
    BigDecimal sumAmountByTypeAndPeriod(
            @Param("walletId") UUID walletId,
            @Param("type") TransactionType type,
            @Param("since") Instant since);
}
