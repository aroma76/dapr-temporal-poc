package com.bank.withdrawal;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * What it does: Implements the Temporal workflow orchestrating the withdrawal process.
 * Why it exists: Encapsulates the step-by-step business flow with durability, retry, and signal waiting.
 * Technology used: Temporal Workflow API (Workflow.newActivityStub, Workflow.await, @SignalMethod).
 * Communication: Calls WithdrawalActivity to publish request, receives signal from DaprEventSubscriber.
 */
public class WithdrawalWorkflowImpl implements WithdrawalWorkflow {

    // Activity stub with automatic retry policy (up to 3 attempts with 1s initial interval)
    private final WithdrawalActivity activity = Workflow.newActivityStub(
            WithdrawalActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(10))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setMaximumAttempts(3)
                            .build())
                    .build()
    );

    // Workflow state variables
    private TransactionResult transactionResult;
    private String status = "INITIALIZED";

    @Override
    public TransactionResult executeWithdrawal(WithdrawalRequest request) {
        // Step 1: Record status and call Activity to publish withdrawal event
        this.status = "PUBLISHING_REQUEST";
        activity.publishWithdrawalRequest(request);

        // Step 2: Wait for transaction result using Workflow.await (timeout after 60 seconds)
        this.status = "WAITING_FOR_RESULT";
        boolean received = Workflow.await(Duration.ofSeconds(60), () -> this.transactionResult != null);

        // Step 3: Check if signal was received or if it timed out
        if (!received || this.transactionResult == null) {
            this.status = "FAILED_TIMEOUT";
            return new TransactionResult(
                    request.getRequestId(),
                    request.getAccountId(),
                    request.getAmount(),
                    "FAILED",
                    "Transaction timed out while waiting for result signal",
                    null
            );
        }

        // Step 4: Return the transaction result received via Signal
        this.status = "COMPLETED";
        return this.transactionResult;
    }

    @Override
    public void transactionResultSignal(TransactionResult result) {
        // Step 4 (Temporal Signal): Receive signal from Withdrawal Service subscriber
        this.transactionResult = result;
        this.status = "SIGNAL_RECEIVED";
    }

    @Override
    public String getStatus() {
        return this.status;
    }
}

