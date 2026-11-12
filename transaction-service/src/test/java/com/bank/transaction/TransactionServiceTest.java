package com.bank.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and integration tests for TransactionService executing against YugabyteDB/H2.
 *
 * Covers:
 * - Test 1: Successful Withdrawal (Initial ₹10,000 - ₹3,000 = ₹7,000)
 * - Test 2: Insufficient Balance (Initial ₹1,000 - attempt ₹5,000 -> Fails, balance stays ₹1,000)
 * - Duplicate withdrawal idempotency using requestId
 */
@SpringBootTest
public class TransactionServiceTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    public void cleanUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    /**
     * Test 1: Successful Withdrawal
     * Initial balance: ₹10,000
     * Withdrawal: ₹3,000
     * Expected balance: ₹7,000
     */
    @Test
    public void testSuccessfulWithdrawal() {
        System.out.println("==================================================");
        System.out.println("Starting Test 1: Successful Withdrawal");
        System.out.println("==================================================");

        // Setup: Create account with initial balance ₹10,000
        String accountId = "ACC-TEST-001";
        transactionService.createOrUpdateAccount(accountId, new BigDecimal("10000.00"));

        // Execute withdrawal of ₹3,000
        WithdrawalRequest request = new WithdrawalRequest("req-test-success-001", accountId, new BigDecimal("3000.00"));
        TransactionResult result = transactionService.processWithdrawal(request);

        System.out.println("[Test 1] Withdrawal Result: " + result);

        // Verify result
        assertEquals("SUCCESS", result.getStatus(), "Status should be SUCCESS");
        assertEquals("Withdrawal successful", result.getMessage());
        assertEquals(new BigDecimal("7000.00"), result.getBalance(), "Result balance should be ₹7,000.00");

        // Verify database balance in YugabyteDB / JPA
        Optional<Account> updatedAccount = accountRepository.findById(accountId);
        assertTrue(updatedAccount.isPresent(), "Account must exist in DB");
        assertEquals(new BigDecimal("7000.00"), updatedAccount.get().getBalance(), "DB balance should be ₹7,000.00");

        // Verify transaction audit log
        Optional<WithdrawalTransaction> tx = transactionRepository.findByRequestId(request.getRequestId());
        assertTrue(tx.isPresent(), "Transaction audit record must be saved");
        assertEquals("SUCCESS", tx.get().getStatus());

        System.out.println("==================================================");
        System.out.println("PASSED Test 1: Successful Withdrawal Verified (₹10,000 -> ₹7,000)!");
        System.out.println("==================================================");
    }

    /**
     * Test 2: Insufficient Balance
     * Initial balance: ₹1,000
     * Withdrawal: ₹5,000
     * Expected: Transaction fails and balance remains ₹1,000
     */
    @Test
    public void testInsufficientBalance() {
        System.out.println("==================================================");
        System.out.println("Starting Test 2: Insufficient Balance");
        System.out.println("==================================================");

        // Setup: Create account with initial balance ₹1,000
        String accountId = "ACC-TEST-002";
        transactionService.createOrUpdateAccount(accountId, new BigDecimal("1000.00"));

        // Execute withdrawal of ₹5,000 (exceeds balance)
        WithdrawalRequest request = new WithdrawalRequest("req-test-fail-001", accountId, new BigDecimal("5000.00"));
        TransactionResult result = transactionService.processWithdrawal(request);

        System.out.println("[Test 2] Withdrawal Result: " + result);

        // Verify result
        assertEquals("FAILED", result.getStatus(), "Status should be FAILED");
        assertEquals("Insufficient balance", result.getMessage());
        assertEquals(new BigDecimal("1000.00"), result.getBalance(), "Balance in result must remain ₹1,000.00");

        // Verify database balance did NOT change
        Optional<Account> account = accountRepository.findById(accountId);
        assertTrue(account.isPresent());
        assertEquals(new BigDecimal("1000.00"), account.get().getBalance(), "DB balance must remain ₹1,000.00");

        // Verify failed transaction is logged for audit
        Optional<WithdrawalTransaction> tx = transactionRepository.findByRequestId(request.getRequestId());
        assertTrue(tx.isPresent(), "Failed transaction audit record must be recorded");
        assertEquals("FAILED", tx.get().getStatus());

        System.out.println("==================================================");
        System.out.println("PASSED Test 2: Insufficient Balance Handled Gracefully!");
        System.out.println("==================================================");
    }

    /**
     * Additional Idempotency Check:
     * Verifies that sending the same requestId does not deduct balance twice.
     */
    @Test
    public void testDuplicateWithdrawalPrevention() {
        System.out.println("==================================================");
        System.out.println("Starting Idempotency Check: Duplicate RequestId");
        System.out.println("==================================================");

        String accountId = "ACC-TEST-003";
        transactionService.createOrUpdateAccount(accountId, new BigDecimal("10000.00"));

        WithdrawalRequest request = new WithdrawalRequest("req-dup-001", accountId, new BigDecimal("2000.00"));

        // First attempt: should succeed and reduce balance to ₹8,000
        TransactionResult result1 = transactionService.processWithdrawal(request);
        assertEquals("SUCCESS", result1.getStatus());
        assertEquals(new BigDecimal("8000.00"), result1.getBalance());

        // Second attempt with exact same requestId: should NOT deduct balance again
        TransactionResult result2 = transactionService.processWithdrawal(request);
        assertTrue(result2.getMessage().contains("Duplicate"));

        // Verify DB balance is still ₹8,000 (NOT ₹6,000!)
        Account account = accountRepository.findById(accountId).orElseThrow();
        assertEquals(new BigDecimal("8000.00"), account.getBalance(), "Duplicate request must not deduct balance twice");

        System.out.println("==================================================");
        System.out.println("PASSED Idempotency Check: Duplicate Request Blocked!");
        System.out.println("==================================================");
    }
}

