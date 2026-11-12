package com.bank.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * What it does: Spring Data JPA repository for the 'withdrawal_transactions' table in YugabyteDB.
 * Why it exists: Provides database access and enables duplicate prevention via findByRequestId.
 * Technology used: Spring Data JPA (JpaRepository).
 * Communication: Injected into and used by TransactionService.
 */
@Repository
public interface TransactionRepository extends JpaRepository<WithdrawalTransaction, Long> {

    /**
     * Finds a previously recorded transaction by requestId to enforce idempotency.
     */
    Optional<WithdrawalTransaction> findByRequestId(String requestId);
}

