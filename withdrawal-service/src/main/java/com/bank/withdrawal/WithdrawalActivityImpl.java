package com.bank.withdrawal;

import io.temporal.activity.Activity;
import io.temporal.failure.ApplicationFailure;
import org.springframework.stereotype.Component;

/**
 * What it does: Implements the WithdrawalActivity executed by Temporal Workers.
 * Why it exists: Performs the external side effect of publishing an event via Dapr Pub/Sub.
 * Technology used: Spring @Component, Temporal Activity implementation.
 * Communication: Invoked by WithdrawalWorkflowImpl, delegates to DaprPubSubService.
 */
@Component
public class WithdrawalActivityImpl implements WithdrawalActivity {

    private final DaprPubSubService daprPubSubService;

    public WithdrawalActivityImpl(DaprPubSubService daprPubSubService) {
        this.daprPubSubService = daprPubSubService;
    }

    @Override
    public void publishWithdrawalRequest(WithdrawalRequest request) {
        // Live Demo Trigger: If requestId starts with "retry-", simulate network failures on attempts 1 & 2
        // so you can watch Temporal's live retry counter directly in the Temporal Web UI (:8080)!
        if (request.getRequestId() != null && request.getRequestId().startsWith("retry-")) {
            int attempt = Activity.getExecutionContext().getInfo().getAttempt();
            if (attempt < 3) {
                System.out.println("[WithdrawalActivityImpl] Simulating network outage on attempt: " + attempt);
                throw ApplicationFailure.newFailure(
                        "Simulated temporary network outage (attempt " + attempt + ")",
                        "NETWORK_ERROR"
                );
            }
            System.out.println("[WithdrawalActivityImpl] Attempt " + attempt + " succeeded! Publishing to Dapr.");
        }

        // Delegates event publication to Dapr Pub/Sub service
        daprPubSubService.publishWithdrawalRequest(request);
    }
}

