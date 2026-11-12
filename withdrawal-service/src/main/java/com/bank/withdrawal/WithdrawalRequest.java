package com.bank.withdrawal;

import java.math.BigDecimal;

/**
 * What it does: Data Transfer Object representing a withdrawal request.
 * Why it exists: Carries the withdrawal input data across HTTP, Temporal Activity, and Dapr Pub/Sub.
 * Technology used: Plain Java POJO with standard Java BigDecimal for financial amounts.
 * Communication: Passed from WithdrawalController -> WithdrawalWorkflow -> WithdrawalActivity -> DaprPubSubService.
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

