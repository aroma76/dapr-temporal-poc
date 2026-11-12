package com.bank.withdrawal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test 3: Temporal Activity Retry
 *
 * Demonstrates:
 * - Attempt 1: Fails
 * - Attempt 2: Fails
 * - Attempt 3: Succeeds
 * Verifies that Temporal automatically retries the Activity according to the RetryOptions.
 */
public class ActivityRetryTest {

    private TestWorkflowEnvironment testEnv;

    @BeforeEach
    public void setUp() {
        // Initializes an in-memory Temporal test server
        testEnv = TestWorkflowEnvironment.newInstance();
    }

    @AfterEach
    public void tearDown() {
        if (testEnv != null) {
            testEnv.close();
        }
    }

    // 1. Activity Interface
    @ActivityInterface
    public interface RetryDemoActivity {
        @ActivityMethod
        String performAction();
    }

    // 2. Workflow Interface
    @WorkflowInterface
    public interface RetryDemoWorkflow {
        @WorkflowMethod
        String runWorkflow();
    }

    // 3. Activity Implementation: Fails twice, succeeds on 3rd attempt
    public static class RetryDemoActivityImpl implements RetryDemoActivity {
        private final AtomicInteger attemptCount = new AtomicInteger(0);

        @Override
        public String performAction() {
            int attempt = attemptCount.incrementAndGet();
            System.out.println("[Test 3 ActivityRetry] Activity invocation attempt: " + attempt);

            if (attempt == 1) {
                System.out.println("[Test 3 ActivityRetry] Attempt 1 failed! Simulating temporary network error.");
                throw ApplicationFailure.newFailure("Temporary network outage (attempt 1)", "NETWORK_ERROR");
            }
            if (attempt == 2) {
                System.out.println("[Test 3 ActivityRetry] Attempt 2 failed! Simulating temporary timeout.");
                throw ApplicationFailure.newFailure("Temporary service timeout (attempt 2)", "TIMEOUT_ERROR");
            }

            System.out.println("[Test 3 ActivityRetry] Attempt 3 succeeded!");
            return "SUCCESS_ON_ATTEMPT_3";
        }

        public int getAttemptCount() {
            return attemptCount.get();
        }
    }

    // 4. Workflow Implementation configuring Temporal automatic retry
    public static class RetryDemoWorkflowImpl implements RetryDemoWorkflow {
        private final RetryDemoActivity activity = Workflow.newActivityStub(
                RetryDemoActivity.class,
                ActivityOptions.newBuilder()
                        .setStartToCloseTimeout(Duration.ofSeconds(5))
                        .setRetryOptions(RetryOptions.newBuilder()
                                .setInitialInterval(Duration.ofMillis(100))
                                .setBackoffCoefficient(1.0)
                                .setMaximumAttempts(3)
                                .build())
                        .build()
        );

        @Override
        public String runWorkflow() {
            return activity.performAction();
        }
    }

    @Test
    public void testActivityAutomaticRetry() {
        System.out.println("==================================================");
        System.out.println("Starting Test 3: Temporal Activity Retry");
        System.out.println("==================================================");

        Worker worker = testEnv.newWorker("RETRY_TEST_QUEUE");
        RetryDemoActivityImpl activityImpl = new RetryDemoActivityImpl();

        worker.registerWorkflowImplementationTypes(RetryDemoWorkflowImpl.class);
        worker.registerActivitiesImplementations(activityImpl);

        testEnv.start();

        RetryDemoWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
                RetryDemoWorkflow.class,
                io.temporal.client.WorkflowOptions.newBuilder()
                        .setTaskQueue("RETRY_TEST_QUEUE")
                        .build()
        );

        String result = workflow.runWorkflow();

        System.out.println("[Test 3 ActivityRetry] Workflow completed with result: " + result);
        System.out.println("[Test 3 ActivityRetry] Total activity attempts recorded: " + activityImpl.getAttemptCount());

        // Verify that exactly 3 attempts took place and the final attempt succeeded
        assertEquals(3, activityImpl.getAttemptCount(), "Temporal should have retried until attempt 3");
        assertEquals("SUCCESS_ON_ATTEMPT_3", result, "Final attempt should return success");

        System.out.println("==================================================");
        System.out.println("PASSED Test 3: Temporal Activity Retry Verified!");
        System.out.println("==================================================");
    }
}

