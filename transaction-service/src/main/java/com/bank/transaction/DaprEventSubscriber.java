package com.bank.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * What it does: Receives withdrawal requests from Dapr topic 'withdrawal-requests' and triggers execution.
 * Why it exists: Acts as the Dapr subscriber webhook in Transaction Service; routes requests into TransactionService.
 * Technology used: Spring @RestController, Jackson ObjectMapper.
 * Communication: Triggered by Dapr Pub/Sub; delegates to TransactionService, then calls DaprPubSubService.
 */
@RestController
public class DaprEventSubscriber {

    private final TransactionService transactionService;
    private final DaprPubSubService daprPubSubService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DaprEventSubscriber(TransactionService transactionService, DaprPubSubService daprPubSubService) {
        this.transactionService = transactionService;
        this.daprPubSubService = daprPubSubService;
    }

    /**
     * Dapr subscription discovery endpoint.
     * Tells Dapr sidecar to deliver messages from 'withdrawal-requests' to '/events/withdrawal-requests'.
     */
    @GetMapping("/dapr/subscribe")
    public List<Map<String, String>> getSubscriptions() {
        return Collections.singletonList(
                Map.of(
                        "pubsubname", "pubsub",
                        "topic", "withdrawal-requests",
                        "route", "/events/withdrawal-requests"
                )
        );
    }

    /**
     * Dapr event handler for topic 'withdrawal-requests'.
     * Processes the withdrawal in YugabyteDB and publishes the result to 'transaction-results'.
     */
    @PostMapping("/events/withdrawal-requests")
    public ResponseEntity<Void> receiveWithdrawalRequest(@RequestBody String payload) {
        try {
            // Parse JSON payload (handles both Dapr CloudEvent JSON and raw JSON)
            JsonNode rootNode = objectMapper.readTree(payload);
            JsonNode dataNode = rootNode.has("data") ? rootNode.get("data") : rootNode;
            WithdrawalRequest request = objectMapper.treeToValue(dataNode, WithdrawalRequest.class);

            System.out.println("[Transaction Service] Received withdrawal request from Pub/Sub: " + request);

            // 1. Process withdrawal against YugabyteDB
            TransactionResult result = transactionService.processWithdrawal(request);

            // 2. Publish transaction result back to Dapr topic 'transaction-results'
            daprPubSubService.publishTransactionResult(result);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.err.println("[Transaction Service] Error handling withdrawal event: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}

