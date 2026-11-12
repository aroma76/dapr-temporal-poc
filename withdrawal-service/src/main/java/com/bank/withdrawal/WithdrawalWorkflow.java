package com.bank.withdrawal;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * What it does: Defines the contract for the Temporal Withdrawal Workflow.
 * Why it exists: Temporal requires an interface annotated with @WorkflowInterface to generate client proxies.
 * Technology used: Temporal Java SDK (@WorkflowInterface, @WorkflowMethod, @SignalMethod, @QueryMethod).
 * Communication: Invoked by WithdrawalController, signaled by DaprEventSubscriber.
 */
@WorkflowInterface
public interface WithdrawalWorkflow {

    /**
     * Main entry point for the workflow.
     * Orchestrates publishing the request and awaiting the transaction result.
     */
    @WorkflowMethod
    TransactionResult executeWithdrawal(WithdrawalRequest request);

    /**
     * Signal method to asynchronously receive the transaction result from Dapr Pub/Sub.
     */
    @SignalMethod
    void transactionResultSignal(TransactionResult result);

    /**
     * Query method to inspect workflow execution status at any point in time.
     */
    @QueryMethod
    String getStatus();
}

