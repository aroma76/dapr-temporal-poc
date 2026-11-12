package com.bank.withdrawal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.client.WorkflowClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * What it does: Listens for incoming transaction results from Dapr Pub/Sub and signals Temporal.
 * Why it exists: Acts as the Dapr subscriber webhook; routes asynchronous results back into the waiting workflow.
 * Technology used: Spring @RestController, Jackson ObjectMapper, Temporal WorkflowClient.
 * Communication: Invoked by Dapr sidecar on topic 'transaction-results'; signals WithdrawalWorkflow.
 */
@RestController
public class DaprEventSubscriber {

    private final WorkflowClient workflowClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public DaprEventSubscriber(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    /**
     * Dapr subscription discovery endpoint.
     * Dapr calls this endpoint on startup to discover which topics this service subscribes to.
     */
    @GetMapping("/dapr/subscribe")
    public List<Map<String, String>> getSubscriptions() {
        return Collections.singletonList(
                Map.of(
                        "pubsubname", "pubsub",
                        "topic", "transaction-results",
                        "route", "/events/transaction-results"
                )
        );
    }

    /**
     * Dapr event handler for topic 'transaction-results'.
     * Receives transaction results and delivers them to the Temporal workflow via Signal.
     */
    @PostMapping("/events/transaction-results")
    public ResponseEntity<Void> receiveTransactionResult(@RequestBody String payload) {
        try {
            // Parse JSON payload (supports both Dapr CloudEvent format and raw JSON)
            JsonNode rootNode = objectMapper.readTree(payload);
            JsonNode dataNode = rootNode.has("data") ? rootNode.get("data") : rootNode;
            TransactionResult result = objectMapper.treeToValue(dataNode, TransactionResult.class);

            System.out.println("[Withdrawal Service] Received TransactionResult event: " + result);

            // Signal the waiting Temporal workflow using its workflowId
            String workflowId = "withdrawal-" + result.getRequestId();
            WithdrawalWorkflow workflow = workflowClient.newWorkflowStub(WithdrawalWorkflow.class, workflowId);
            workflow.transactionResultSignal(result);

            System.out.println("[Withdrawal Service] Successfully signaled workflow: " + workflowId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.err.println("[Withdrawal Service] Error processing transaction result: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}

