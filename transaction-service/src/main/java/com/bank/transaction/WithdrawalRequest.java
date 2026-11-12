package com.bank.transaction;

import java.math.BigDecimal;

/**
 * What it does: Data Transfer Object representing a withdrawal request.
 * Why it exists: Receives the incoming withdrawal payload from Dapr Pub/Sub topic 'withdrawal-requests'.
 * Technology used: Plain Java POJO with BigDecimal.
 * Communication: Received by DaprEventSubscriber and passed into TransactionService.
 */
public class WithdrawalRequest {

    private String requestId;
    private String accountId;
    private BigDecimal amount;

    public WithdrawalRequest() {
    }

    public WithdrawalRequest(String requestId, String accountId, BigDecimal amount) {
        this.requestId = requestId;
        this.accountId = accountId;
        this.amount = amount;
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

    @Override
    public String toString() {
        return "WithdrawalRequest{" +
                "requestId='" + requestId + '\'' +
                ", accountId='" + accountId + '\'' +
                ", amount=" + amount +
                '}';
    }
}

