package com.bank.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * What it does: Implements core banking business logic for withdrawals and account updates.
 * Why it exists: Enforces business invariants (idempotency, balance verification, atomicity) in YugabyteDB.
 * Technology used: Spring @Service, Spring @Transactional, Spring Data JPA.
 * Communication: Called by DaprEventSubscriber and TransactionController; interacts with AccountRepository and TransactionRepository.
 */
@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Processes a withdrawal request atomically against YugabyteDB.
     * Prevents duplicate withdrawals using requestId.
     */
    @Transactional
    public TransactionResult processWithdrawal(WithdrawalRequest request) {
        // Step 1: Idempotency check - prevent duplicate withdrawals using requestId
        Optional<WithdrawalTransaction> existing = transactionRepository.findByRequestId(request.getRequestId());
        if (existing.isPresent()) {
            WithdrawalTransaction previousTx = existing.get();
            System.out.println("[TransactionService] Duplicate request detected for requestId: " + request.getRequestId());
            Account currentAcc = accountRepository.findById(previousTx.getAccountId()).orElse(null);
            BigDecimal currentBalance = currentAcc != null ? currentAcc.getBalance() : BigDecimal.ZERO;
            return new TransactionResult(
                    previousTx.getRequestId(),
                    previousTx.getAccountId(),
                    previousTx.getAmount(),
                    previousTx.getStatus(),
                    "Duplicate request - returned existing result",
                    currentBalance
            );
        }

        // Step 2: Account lookup
        Account account = accountRepository.findById(request.getAccountId()).orElse(null);
        if (account == null) {
            WithdrawalTransaction failedTx = new WithdrawalTransaction(
                    request.getRequestId(),
                    request.getAccountId(),
                    request.getAmount(),
                    "FAILED",
                    "Account not found",
                    LocalDateTime.now()
            );
            transactionRepository.save(failedTx);
            return new TransactionResult(
                    request.getRequestId(),
                    request.getAccountId(),
                    request.getAmount(),
                    "FAILED",
                    "Account not found",
                    BigDecimal.ZERO
            );
        }

        // Step 3: Balance verification (Test 2: Insufficient balance check)
        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            WithdrawalTransaction failedTx = new WithdrawalTransaction(
                    request.getRequestId(),
                    request.getAccountId(),
                    request.getAmount(),
                    "FAILED",
                    "Insufficient balance",
                    LocalDateTime.now()
            );
            transactionRepository.save(failedTx);
            return new TransactionResult(
                    request.getRequestId(),
                    request.getAccountId(),
                    request.getAmount(),
                    "FAILED",
                    "Insufficient balance",
                    account.getBalance()
            );
        }

        // Step 4: Successful withdrawal - deduct balance and record audit entry (Test 1)
        account.setBalance(account.getBalance().subtract(request.getAmount()));
        accountRepository.save(account);

        WithdrawalTransaction successTx = new WithdrawalTransaction(
                request.getRequestId(),
                request.getAccountId(),
                request.getAmount(),
                "SUCCESS",
                "Withdrawal successful",
                LocalDateTime.now()
        );
        transactionRepository.save(successTx);

        return new TransactionResult(
                request.getRequestId(),
                request.getAccountId(),
                request.getAmount(),
                "SUCCESS",
                "Withdrawal successful",
                account.getBalance()
        );
    }

    /**
     * Helper to create or initialize an account with an initial balance.
     */
    @Transactional
    public Account createOrUpdateAccount(String accountId, BigDecimal balance) {
        return accountRepository.save(new Account(accountId, balance));
    }

    /**
     * Helper to fetch an account by ID.
     */
    public Optional<Account> getAccount(String accountId) {
        return accountRepository.findById(accountId);
    }

    /**
     * Helper to retrieve all recorded withdrawal transactions.
     */
    public List<WithdrawalTransaction> getAllTransactions() {
        return transactionRepository.findAll();
    }
}

