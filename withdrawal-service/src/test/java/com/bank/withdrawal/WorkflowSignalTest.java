package com.bank.withdrawal;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test 4: Temporal Signal
 *
 * Demonstrates:
 * 1. Start the workflow asynchronously.
 * 2. Let the workflow wait for a transaction result (Workflow.await).
 * 3. Verify that the workflow is still running in WAITING_FOR_RESULT state.
 * 4. Send TransactionCompleted via Temporal Signal (transactionResultSignal).
 * 5. Verify that the workflow unblocks and completes with the expected result.
 */
public class WorkflowSignalTest {

    private TestWorkflowEnvironment testEnv;

    @BeforeEach
    public void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
    }

    @AfterEach
    public void tearDown() {
        if (testEnv != null) {
            testEnv.close();
        }
    }

    // Dummy activity for testing that simulates publishing to Dapr
    public static class TestWithdrawalActivityImpl implements WithdrawalActivity {
        private boolean published = false;

        @Override
        public void publishWithdrawalRequest(WithdrawalRequest request) {
            System.out.println("[Test 4 Signal] Activity published withdrawal request for: " + request.getRequestId());
            this.published = true;
        }

        public boolean isPublished() {
            return published;
        }
    }

    @Test
    public void testWorkflowSignalHandling() {
        System.out.println("==================================================");
        System.out.println("Starting Test 4: Temporal Signal Verification");
        System.out.println("==================================================");

        String taskQueue = "SIGNAL_TEST_QUEUE";
        Worker worker = testEnv.newWorker(taskQueue);
        TestWithdrawalActivityImpl activityImpl = new TestWithdrawalActivityImpl();

        worker.registerWorkflowImplementationTypes(WithdrawalWorkflowImpl.class);
        worker.registerActivitiesImplementations(activityImpl);

        testEnv.start();

        String requestId = "req-signal-demo-001";
        WithdrawalRequest request = new WithdrawalRequest(requestId, "ACC-101", new BigDecimal("3000"));

        // Create workflow stub
        WithdrawalWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
                WithdrawalWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("withdrawal-" + requestId)
                        .setTaskQueue(taskQueue)
                        .build()
        );

        // Step 1: Start the workflow asynchronously
        System.out.println("[Test 4 Signal] Step 1: Starting workflow asynchronously...");
        WorkflowClient.start(workflow::executeWithdrawal, request);

        // Step 2 & 3: Let it execute the activity and enter Workflow.await
        testEnv.sleep(Duration.ofMillis(500));

        // Verify that the workflow is running and waiting for the signal
        String currentStatus = workflow.getStatus();
        System.out.println("[Test 4 Signal] Step 2 & 3: Current Workflow Status: " + currentStatus);
        assertEquals("WAITING_FOR_RESULT", currentStatus, "Workflow must be waiting for transaction result");

        // Step 4: Deliver TransactionCompleted result through a Signal
        System.out.println("[Test 4 Signal] Step 4: Delivering TransactionResult via Temporal Signal...");
        TransactionResult expectedResult = new TransactionResult(
                requestId,
                "ACC-101",
                new BigDecimal("3000"),
                "SUCCESS",
                "Withdrawal completed successfully",
                new BigDecimal("7000")
        );
        workflow.transactionResultSignal(expectedResult);

        // Step 5: Verify that the workflow completes and returns the signaled result
        System.out.println("[Test 4 Signal] Step 5: Waiting for workflow completion...");
        WorkflowStub untypedStub = WorkflowStub.fromTyped(workflow);
        TransactionResult finalResult = untypedStub.getResult(TransactionResult.class);

        System.out.println("[Test 4 Signal] Final Workflow Output: " + finalResult);
        assertNotNull(finalResult, "Workflow should produce a result");
        assertEquals("SUCCESS", finalResult.getStatus(), "Transaction status should be SUCCESS");
        assertEquals(new BigDecimal("7000"), finalResult.getBalance(), "Updated balance should be ₹7,000");

        System.out.println("==================================================");
        System.out.println("PASSED Test 4: Temporal Signal Successfully Handled!");
        System.out.println("==================================================");
    }
}

