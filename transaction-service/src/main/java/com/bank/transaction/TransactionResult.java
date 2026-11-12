package com.bank.transaction;

import java.math.BigDecimal;

/**
 * What it does: Data Transfer Object representing the result of a processed withdrawal.
 * Why it exists: Transports the final status, message, and balance back to Withdrawal Service via Dapr Pub/Sub.
 * Technology used: Plain Java POJO with BigDecimal.
 * Communication: Produced by TransactionService, published by DaprPubSubService to topic 'transaction-results'.
 */
public class TransactionResult {

    private String requestId;
    private String accountId;
    private BigDecimal amount;
    private String status;   // "SUCCESS" or "FAILED"
    private String message;  // e.g. "Withdrawal successful", "Insufficient balance"
    private BigDecimal balance;

    public TransactionResult() {
    }

    public TransactionResult(String requestId, String accountId, BigDecimal amount,
                             String status, String message, BigDecimal balance) {
        this.requestId = requestId;
        this.accountId = accountId;
        this.amount = amount;
        this.status = status;
        this.message = message;
        this.balance = balance;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    @Override
    public String toString() {
        return "TransactionResult{" +
                "requestId='" + requestId + '\'' +
                ", accountId='" + accountId + '\'' +
                ", amount=" + amount +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", balance=" + balance +
                '}';
    }
}

