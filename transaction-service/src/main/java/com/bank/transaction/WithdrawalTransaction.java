package com.bank.transaction;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What it does: JPA entity representing an audit record of a withdrawal transaction in YugabyteDB.
 * Why it exists: Maps directly to 'withdrawal_transactions' table and uses unique 'requestId' to enforce idempotency.
 * Technology used: Jakarta Persistence API (JPA / Hibernate) mapped to YugabyteDB.
 * Communication: Saved and queried by TransactionRepository and TransactionService.
 */
@Entity
@Table(name = "withdrawal_transactions")
public class WithdrawalTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", unique = true, nullable = false, length = 64)
    private String requestId;

    @Column(name = "account_id", nullable = false, length = 50)
    private String accountId;

    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // "SUCCESS" or "FAILED"

    @Column(name = "message", length = 255)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public WithdrawalTransaction() {
    }

    public WithdrawalTransaction(String requestId, String accountId, BigDecimal amount,
                                 String status, String message, LocalDateTime createdAt) {
        this.requestId = requestId;
        this.accountId = accountId;
        this.amount = amount;
        this.status = status;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "WithdrawalTransaction{" +
                "id=" + id +
                ", requestId='" + requestId + '\'' +
                ", accountId='" + accountId + '\'' +
                ", amount=" + amount +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

