package com.bank.withdrawal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * What it does: Defines the Activity interface for publishing withdrawal requests.
 * Why it exists: Temporal requires an interface annotated with @ActivityInterface to execute non-deterministic
 *                or external side effects (like network calls to Dapr Pub/Sub) with automatic retry guarantees.
 * Technology used: Temporal Java SDK (@ActivityInterface, @ActivityMethod).
 * Communication: Called by WithdrawalWorkflowImpl, implemented by WithdrawalActivityImpl.
 */
@ActivityInterface
public interface WithdrawalActivity {

    /**
     * Publishes the withdrawal request to the Dapr topic 'withdrawal-requests'.
     */
    @ActivityMethod
    void publishWithdrawalRequest(WithdrawalRequest request);
}

