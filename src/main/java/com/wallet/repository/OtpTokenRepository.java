package com.wallet.repository;

import com.wallet.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, UUID> {

    Optional<OtpToken> findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(
            String userId, String purpose);
}
