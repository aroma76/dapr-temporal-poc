package com.bank.transaction;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * What it does: Exposes REST endpoints to query and seed bank accounts and inspect transaction logs.
 * Why it exists: Enables Postman, curl, and presentation attendees to view account balances and verify database state.
 * Technology used: Spring Web MVC (@RestController, @RequestMapping).
 * Communication: Interacts with TransactionService to query YugabyteDB.
 */
@RestController
@RequestMapping("/api")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Seeds or updates an account with an initial balance.
     * Useful for setting up Test 1 (₹10,000) and Test 2 (₹1,000).
     */
    @PostMapping("/accounts")
    public ResponseEntity<Account> createAccount(@RequestBody Map<String, Object> body) {
        String accountId = (String) body.get("accountId");
        BigDecimal balance = new BigDecimal(body.get("balance").toString());
        Account saved = transactionService.createOrUpdateAccount(accountId, balance);
        return ResponseEntity.ok(saved);
    }

    /**
     * Queries current account details and balance from YugabyteDB.
     */
    @GetMapping("/accounts/{id}")
    public ResponseEntity<Account> getAccount(@PathVariable("id") String id) {
        return transactionService.getAccount(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lists all withdrawal transactions recorded in YugabyteDB.
     */
    @GetMapping("/transactions")
    public ResponseEntity<List<WithdrawalTransaction>> getAllTransactions() {
        return ResponseEntity.ok(transactionService.getAllTransactions());
    }
}

