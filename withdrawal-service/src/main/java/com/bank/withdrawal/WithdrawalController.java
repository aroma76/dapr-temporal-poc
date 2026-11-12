package com.bank.withdrawal;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * What it does: Exposes REST API endpoints to initiate and monitor withdrawal workflows.
 * Why it exists: Provides an HTTP entry point for Postman, UI, or curl clients to trigger banking withdrawals.
 * Technology used: Spring Web MVC (@RestController, @PostMapping, @GetMapping), Temporal WorkflowClient.
 * Communication: Receives HTTP requests and creates/invokes Temporal WithdrawalWorkflow instances.
 */
@RestController
@RequestMapping("/api/withdrawals")
public class WithdrawalController {

    private final WorkflowClient workflowClient;

    @Autowired
    public WithdrawalController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    /**
     * Synchronous withdrawal endpoint.
     * Starts the workflow and waits for completion, returning the final TransactionResult.
     */
    @PostMapping
    public ResponseEntity<TransactionResult> withdraw(@RequestBody WithdrawalRequest request) {
        System.out.println("[WithdrawalController] Received withdrawal request: " + request);

        // Configure workflow options with unique workflowId based on requestId
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId("withdrawal-" + request.getRequestId())
                .setTaskQueue(TemporalConfig.TASK_QUEUE)
                .build();

        // Create workflow stub and execute
        WithdrawalWorkflow workflow = workflowClient.newWorkflowStub(WithdrawalWorkflow.class, options);
        TransactionResult result = workflow.executeWithdrawal(request);

        System.out.println("[WithdrawalController] Completed withdrawal workflow with result: " + result);
        return ResponseEntity.ok(result);
    }

    /**
     * Asynchronous withdrawal endpoint.
     * Starts the workflow in the background and immediately returns the workflow ID.
     */
    @PostMapping("/async")
    public ResponseEntity<Map<String, String>> withdrawAsync(@RequestBody WithdrawalRequest request) {
        String workflowId = "withdrawal-" + request.getRequestId();

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TemporalConfig.TASK_QUEUE)
                .build();

        WithdrawalWorkflow workflow = workflowClient.newWorkflowStub(WithdrawalWorkflow.class, options);
        WorkflowClient.start(workflow::executeWithdrawal, request);

        return ResponseEntity.accepted().body(Map.of(
                "workflowId", workflowId,
                "requestId", request.getRequestId(),
                "status", "RUNNING"
        ));
    }

    /**
     * Query endpoint to check the live status of an active or completed workflow.
     */
    @GetMapping("/{requestId}/status")
    public ResponseEntity<Map<String, String>> getStatus(@PathVariable("requestId") String requestId) {
        String workflowId = "withdrawal-" + requestId;
        try {
            WithdrawalWorkflow workflow = workflowClient.newWorkflowStub(WithdrawalWorkflow.class, workflowId);
            String status = workflow.getStatus();
            return ResponseEntity.ok(Map.of("workflowId", workflowId, "status", status));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("workflowId", workflowId, "error", e.getMessage()));
        }
    }
}

