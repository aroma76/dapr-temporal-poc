package com.bank.withdrawal;

import java.math.BigDecimal;

/**
 * What it does: Data Transfer Object representing the outcome of a withdrawal transaction.
 * Why it exists: Holds the transaction status, message, and updated account balance.
 * Technology used: Plain Java POJO with BigDecimal.
 * Communication: Created by Transaction Service, received via Dapr Pub/Sub into DaprEventSubscriber,
 *                and delivered as a Signal to WithdrawalWorkflow.
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

